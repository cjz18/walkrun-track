package com.cjz18.walkrun.tracking

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object TrackStats {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MIN_PACE_DISTANCE_METERS = 20.0
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun distanceMeters(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (index in 1 until points.size) {
            total += haversineMeters(points[index - 1], points[index])
        }
        return total
    }

    fun formatStats(distanceMeters: Double, durationMillis: Long): String {
        val pace = paceSecPerKm(distanceMeters, durationMillis)
        return "${formatDistance(distanceMeters)} · ${formatDuration(durationMillis)} · ${formatPace(pace)}"
    }

    fun formatHistoryRow(
        startedAtEpochMs: Long,
        distanceMeters: Double,
        durationMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        return "${formatDate(startedAtEpochMs, zone)} · ${formatDistance(distanceMeters)} · ${formatDuration(durationMillis)}"
    }

    fun formatDistance(meters: Double): String {
        val safe = if (meters.isNaN()) 0.0 else meters.coerceAtLeast(0.0)
        return if (safe < 1000.0) {
            "${safe.roundToInt()} m"
        } else {
            String.format(Locale.US, "%.2f km", safe / 1000.0)
        }
    }

    fun formatDuration(millis: Long): String {
        val totalSec = millis.coerceAtLeast(0L) / 1000L
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatPace(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm.isNaN() || secPerKm.isInfinite()) return "--"
        val total = secPerKm.roundToInt().coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return String.format(Locale.US, "%d'%02d\"/km", minutes, seconds)
    }

    fun formatDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(dateFormatter)

    private fun paceSecPerKm(distanceMeters: Double, durationMillis: Long): Double? {
        if (distanceMeters < MIN_PACE_DISTANCE_METERS || durationMillis <= 0L) return null
        return (durationMillis / 1000.0) / (distanceMeters / 1000.0)
    }

    private fun haversineMeters(start: TrackPoint, end: TrackPoint): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }
}
