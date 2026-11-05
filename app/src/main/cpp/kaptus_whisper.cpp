#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <whisper.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <vector>
#include <string>

namespace {

struct RecognizerContext {
    whisper_context *context;
    std::atomic<bool> aborted{false};
};

whisper_context *whisperContext(jlong pointer) {
    return pointer == 0 ? nullptr : reinterpret_cast<RecognizerContext *>(pointer)->context;
}

bool abortRequested(void *context) {
    return static_cast<RecognizerContext *>(context)->aborted.load();
}

// JNI NewStringUTF expects modified UTF-8. Whisper emits ordinary UTF-8, including
// byte-split tokens, so expose token bytes and decode complete sequences in Kotlin.
jbyteArray bytes(JNIEnv *environment, const char *text) {
    const auto count = static_cast<jsize>(text == nullptr ? 0 : std::strlen(text));
    jbyteArray result = environment->NewByteArray(count);
    if (count > 0) environment->SetByteArrayRegion(result, 0, count, reinterpret_cast<const jbyte *>(text));
    return result;
}

jstring unicodeString(JNIEnv *environment, const char *text) {
    std::vector<jchar> output;
    const auto *input = reinterpret_cast<const unsigned char *>(text == nullptr ? "" : text);
    const size_t count = std::strlen(reinterpret_cast<const char *>(input));
    for (size_t index = 0; index < count;) {
        uint32_t point = input[index++];
        int extra = 0;
        if (point >= 0xc2 && point <= 0xdf) { point &= 0x1f; extra = 1; }
        else if (point >= 0xe0 && point <= 0xef) { point &= 0x0f; extra = 2; }
        else if (point >= 0xf0 && point <= 0xf4) { point &= 0x07; extra = 3; }
        else if (point >= 0x80) { output.push_back(0xfffd); continue; }
        bool valid = index + extra <= count;
        for (int offset = 0; valid && offset < extra; ++offset) {
            const unsigned char next = input[index + offset];
            valid = (next & 0xc0) == 0x80;
            point = (point << 6) | (next & 0x3f);
        }
        if (!valid || point > 0x10ffff || (point >= 0xd800 && point <= 0xdfff) ||
            (extra == 1 && point < 0x80) || (extra == 2 && point < 0x800) || (extra == 3 && point < 0x10000)) {
            output.push_back(0xfffd); continue;
        }
        index += extra;
        if (point <= 0xffff) output.push_back(static_cast<jchar>(point));
        else { point -= 0x10000; output.push_back(0xd800 + (point >> 10)); output.push_back(0xdc00 + (point & 0x3ff)); }
    }
    return environment->NewString(output.data(), static_cast<jsize>(output.size()));
}

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
    auto *context = loadAsset<whisper_context>(environment, assetManager, path, false);
    return context == nullptr ? 0 : reinterpret_cast<jlong>(new RecognizerContext{context});
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_initWhisperFile(JNIEnv *environment, jobject, jstring path) {
    const char *chars = environment->GetStringUTFChars(path, nullptr);
    auto *context = whisper_init_from_file_with_params(chars, whisper_context_default_params());
    environment->ReleaseStringUTFChars(path, chars);
    return context == nullptr ? 0 : reinterpret_cast<jlong>(new RecognizerContext{context});
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
    if (pointer != 0) {
        auto *context = reinterpret_cast<RecognizerContext *>(pointer);
        whisper_free(context->context);
        delete context;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_kaptus_speech_WhisperNative_resetAbort(JNIEnv *, jobject, jlong pointer) {
    if (pointer != 0) reinterpret_cast<RecognizerContext *>(pointer)->aborted.store(false);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_kaptus_speech_WhisperNative_abort(JNIEnv *, jobject, jlong pointer) {
    if (pointer != 0) reinterpret_cast<RecognizerContext *>(pointer)->aborted.store(true);
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
    jint threads,
    jstring language,
    jboolean translate
) {
    if (pointer == 0) return -1;
    auto *recognizer = reinterpret_cast<RecognizerContext *>(pointer);
    auto *context = recognizer->context;
    const jsize count = environment->GetArrayLength(audio);
    jfloat *samples = environment->GetFloatArrayElements(audio, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    const char *languageChars = environment->GetStringUTFChars(language, nullptr);
    params.language = languageChars;
    params.n_threads = std::max(1, static_cast<int>(threads));
    params.translate = translate == JNI_TRUE;
    params.no_context = true;
    params.single_segment = false;
    params.token_timestamps = translate != JNI_TRUE;
    params.abort_callback = abortRequested;
    params.abort_callback_user_data = recognizer;
    params.max_len = 0;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.suppress_nst = true;

    const int result = whisper_full(context, params, samples, count);
    environment->ReleaseStringUTFChars(language, languageChars);
    environment->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_kaptus_speech_WhisperNative_segmentCount(JNIEnv *, jobject, jlong pointer) {
    if (pointer == 0) return 0;
    return whisper_full_n_segments(whisperContext(pointer));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenCount(
    JNIEnv *, jobject, jlong pointer, jint segment
) {
    if (pointer == 0) return 0;
    return whisper_full_n_tokens(whisperContext(pointer), segment);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenText(
    JNIEnv *environment,
    jobject,
    jlong pointer,
    jint segment,
    jint token
) {
    if (pointer == 0) return unicodeString(environment, "");
    auto *context = whisperContext(pointer);
    const whisper_token id = whisper_full_get_token_id(context, segment, token);
    const char *text = whisper_token_to_str(context, id);
    return unicodeString(environment, text);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenBytes(JNIEnv *environment, jobject, jlong pointer, jint segment, jint token) {
    auto *context = whisperContext(pointer);
    return bytes(environment, context == nullptr ? "" : whisper_token_to_str(context, whisper_full_get_token_id(context, segment, token)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_segmentStart(JNIEnv *, jobject, jlong pointer, jint segment) {
    return pointer == 0 ? -1 : whisper_full_get_segment_t0(whisperContext(pointer), segment);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_segmentEnd(JNIEnv *, jobject, jlong pointer, jint segment) {
    return pointer == 0 ? -1 : whisper_full_get_segment_t1(whisperContext(pointer), segment);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenStart(
    JNIEnv *, jobject, jlong pointer, jint segment, jint token
) {
    if (pointer == 0) return -1;
    return whisper_full_get_token_data(
        whisperContext(pointer), segment, token
    ).t0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenEnd(
    JNIEnv *, jobject, jlong pointer, jint segment, jint token
) {
    if (pointer == 0) return -1;
    return whisper_full_get_token_data(
        whisperContext(pointer), segment, token
    ).t1;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_kaptus_speech_WhisperNative_tokenProbability(
    JNIEnv *, jobject, jlong pointer, jint segment, jint token
) {
    if (pointer == 0) return 0.0f;
    return whisper_full_get_token_data(
        whisperContext(pointer), segment, token
    ).p;
}
