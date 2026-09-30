package com.example.kaptus.speech

import android.content.res.AssetManager

internal object WhisperNative {
    init {
        System.loadLibrary("kaptus_whisper")
    }

    external fun initWhisper(assetManager: AssetManager, path: String): Long
    external fun initWhisperFile(path: String): Long
    external fun initVad(assetManager: AssetManager, path: String): Long
    external fun freeWhisper(pointer: Long)
    external fun freeVad(pointer: Long)
    external fun hasSpeech(pointer: Long, audio: FloatArray): Boolean
    external fun resetAbort(pointer: Long)
    external fun abort(pointer: Long)
    external fun transcribe(pointer: Long, audio: FloatArray, threads: Int, language: String, translate: Boolean): Int
    external fun segmentCount(pointer: Long): Int
    external fun tokenCount(pointer: Long, segment: Int): Int
    external fun tokenText(pointer: Long, segment: Int, token: Int): String
    external fun tokenBytes(pointer: Long, segment: Int, token: Int): ByteArray
    external fun segmentStart(pointer: Long, segment: Int): Long
    external fun segmentEnd(pointer: Long, segment: Int): Long
    external fun tokenStart(pointer: Long, segment: Int, token: Int): Long
    external fun tokenEnd(pointer: Long, segment: Int, token: Int): Long
    external fun tokenProbability(pointer: Long, segment: Int, token: Int): Float
}
