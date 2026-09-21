package com.example.kaptus.sync

object SyncTuning {
    const val INITIAL_MATCH_SCORE = 0.48f
    const val TRACKING_MATCH_SCORE = 0.46f
    const val MINIMUM_CONTENT_WORDS = 3
    const val MINIMUM_RUNNER_UP_MARGIN = 0.05f
    const val MAXIMUM_ANCHOR_DISAGREEMENT_MS = 1_250L
    const val ACQUISITION_TIMEOUT_SECONDS = 30
    const val SMALL_CORRECTION_MS = 750L
    const val CORRECTION_SLEW_DURATION_MS = 2_000L
    const val RATE_ESTIMATION_SPAN_MS = 60_000.0
    const val MINIMUM_PLAYBACK_RATE = 0.95
    const val MAXIMUM_PLAYBACK_RATE = 1.05
}
