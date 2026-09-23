package com.cjz18.walkrun.tracking

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timeEpochMs: Long,
    val accuracyMeters: Float,
)
