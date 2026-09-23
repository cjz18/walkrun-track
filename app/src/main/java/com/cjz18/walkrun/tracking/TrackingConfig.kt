package com.cjz18.walkrun.tracking

object TrackingConfig {
    const val SAMPLE_INTERVAL_MS = 2_000L
    const val WEAK_ACCURACY_METERS = 50f

    fun isWeakAccuracy(meters: Float): Boolean =
        meters <= 0f || meters > WEAK_ACCURACY_METERS
}
