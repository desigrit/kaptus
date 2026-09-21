package com.example.kaptus.sync

enum class SyncErrorKind {
    NO_MATCH,
    MICROPHONE_PERMISSION,
    RECOGNIZER
}

enum class ReacquisitionReason {
    USER_REQUEST,
    BACKGROUNDED,
    TIMELINE_MOVED,
    DIALOGUE_MISMATCH
}

enum class AcquisitionPhase {
    HEARING_DIALOGUE,
    TRANSCRIBING,
    COMPARING_CAPTIONS
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Listening : SyncState
    data class Acquiring(
        val secondsListening: Int = 0,
        val phase: AcquisitionPhase = AcquisitionPhase.HEARING_DIALOGUE
    ) : SyncState
    data class Synchronized(
        val confidence: Float,
        val lastConfirmedRealtimeMs: Long,
        val checking: Boolean = false,
        val pendingLargeCorrection: Boolean = false
    ) : SyncState
    data class Reacquiring(
        val reason: ReacquisitionReason,
        val phase: AcquisitionPhase = AcquisitionPhase.HEARING_DIALOGUE
    ) : SyncState
    data class Error(val kind: SyncErrorKind = SyncErrorKind.RECOGNIZER) : SyncState
}
