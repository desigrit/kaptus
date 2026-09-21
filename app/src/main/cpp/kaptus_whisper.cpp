#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <whisper.h>

#include <algorithm>
#include <string>

namespace {

struct AssetContext {
    AAsset *asset;
};

size_t assetRead(void *context, void *output, size_t size) {
    auto *assetContext = static_cast<AssetContext *>(context);
    return static_cast<size_t>(AAsset_read(assetContext->asset, output, size));
}

bool assetEof(void *context) {
    auto *assetContext = static_cast<AssetContext *>(context);
    return AAsset_getRemainingLength64(assetContext->asset) <= 0;
}

void assetClose(void *context) {
    auto *assetContext = static_cast<AssetContext *>(context);
    if (assetContext->asset != nullptr) {
        AAsset_close(assetContext->asset);
        assetContext->asset = nullptr;
    }
}

template <typename T>
T *loadAsset(JNIEnv *environment, jobject assetManager, jstring path, bool vad) {
    const char *pathChars = environment->GetStringUTFChars(path, nullptr);
    AAssetManager *manager = AAssetManager_fromJava(environment, assetManager);
    AAsset *asset = AAssetManager_open(manager, pathChars, AASSET_MODE_STREAMING);
    environment->ReleaseStringUTFChars(path, pathChars);
    if (asset == nullptr) return nullptr;

    AssetContext context{asset};
    whisper_model_loader loader{
        .context = &context,
        .read = assetRead,
        .eof = assetEof,
        .close = assetClose,
    };

    if (vad) {
        return reinterpret_cast<T *>(whisper_vad_init_with_params(
            &loader,
            whisper_vad_default_context_params()
        ));
    }
    return reinterpret_cast<T *>(whisper_init_with_params(
        &loader,
        whisper_context_default_params()
    ));
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_initWhisper(
    JNIEnv *environment,
    jobject,
    jobject assetManager,
    jstring path
) {
    return reinterpret_cast<jlong>(
        loadAsset<whisper_context>(environment, assetManager, path, false)
    );
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_initVad(
    JNIEnv *environment,
    jobject,
    jobject assetManager,
    jstring path
) {
    return reinterpret_cast<jlong>(
        loadAsset<whisper_vad_context>(environment, assetManager, path, true)
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_kaptus_speech_WhisperNative_freeWhisper(JNIEnv *, jobject, jlong pointer) {
    if (pointer != 0) whisper_free(reinterpret_cast<whisper_context *>(pointer));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_kaptus_speech_WhisperNative_freeVad(JNIEnv *, jobject, jlong pointer) {
    if (pointer != 0) whisper_vad_free(reinterpret_cast<whisper_vad_context *>(pointer));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_kaptus_speech_WhisperNative_hasSpeech(
    JNIEnv *environment,
    jobject,
    jlong pointer,
    jfloatArray audio
) {
    if (pointer == 0) return JNI_TRUE;
    auto *vad = reinterpret_cast<whisper_vad_context *>(pointer);
    const jsize count = environment->GetArrayLength(audio);
    jfloat *samples = environment->GetFloatArrayElements(audio, nullptr);
    whisper_vad_params params = whisper_vad_default_params();
    params.threshold = 0.50f;
    params.min_speech_duration_ms = 250;
    whisper_vad_segments *segments = whisper_vad_segments_from_samples(vad, params, samples, count);
    environment->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    if (segments == nullptr) return JNI_TRUE;
    const bool found = whisper_vad_segments_n_segments(segments) > 0;
    whisper_vad_free_segments(segments);
    return found ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_kaptus_speech_WhisperNative_transcribe(
    JNIEnv *environment,
    jobject,
    jlong pointer,
    jfloatArray audio,
    jint threads
) {
    if (pointer == 0) return -1;
    auto *context = reinterpret_cast<whisper_context *>(pointer);
    const jsize count = environment->GetArrayLength(audio);
    jfloat *samples = environment->GetFloatArrayElements(audio, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "en";
    params.n_threads = std::max(1, static_cast<int>(threads));
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.token_timestamps = true;
    params.max_len = 0;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.suppress_nst = true;

    const int result = whisper_full(context, params, samples, count);
    environment->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_kaptus_speech_WhisperNative_segmentCount(JNIEnv *, jobject, jlong pointer) {
    if (pointer == 0) return 0;
    return whisper_full_n_segments(reinterpret_cast<whisper_context *>(pointer));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenCount(
    JNIEnv *, jobject, jlong pointer, jint segment
) {
    if (pointer == 0) return 0;
    return whisper_full_n_tokens(reinterpret_cast<whisper_context *>(pointer), segment);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenText(
    JNIEnv *environment,
    jobject,
    jlong pointer,
    jint segment,
    jint token
) {
    if (pointer == 0) return environment->NewStringUTF("");
    auto *context = reinterpret_cast<whisper_context *>(pointer);
    const whisper_token id = whisper_full_get_token_id(context, segment, token);
    const char *text = whisper_token_to_str(context, id);
    return environment->NewStringUTF(text == nullptr ? "" : text);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenStart(
    JNIEnv *, jobject, jlong pointer, jint segment, jint token
) {
    if (pointer == 0) return -1;
    return whisper_full_get_token_data(
        reinterpret_cast<whisper_context *>(pointer), segment, token
    ).t0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenEnd(
    JNIEnv *, jobject, jlong pointer, jint segment, jint token
) {
    if (pointer == 0) return -1;
    return whisper_full_get_token_data(
        reinterpret_cast<whisper_context *>(pointer), segment, token
    ).t1;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenProbability(
    JNIEnv *, jobject, jlong pointer, jint segment, jint token
) {
    if (pointer == 0) return 0.0f;
    return whisper_full_get_token_data(
        reinterpret_cast<whisper_context *>(pointer), segment, token
    ).p;
}
