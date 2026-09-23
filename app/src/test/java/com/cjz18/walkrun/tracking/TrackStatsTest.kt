package com.cjz18.walkrun.tracking

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackStatsTest {
    @Test
    fun oneKilometerNorthIsAboutOneKilometer() {
        val start = TrackPoint(39.9, 116.4, 0L, 5f)
        val end = TrackPoint(39.908983, 116.4, 1L, 5f)
        val meters = TrackStats.distanceMeters(listOf(start, end))
        assertTrue("distance=$meters", meters in 980.0..1020.0)
    }

    @Test
    fun statsLineOrdersDistanceDurationPace() {
        assertEquals("1.00 km · 06:00 · 6'00\"/km", TrackStats.formatStats(1000.0, 360_000))
    }

    @Test
    fun shortDistanceHasNoPace() {
        assertEquals("0 m · 00:05 · --", TrackStats.formatStats(0.0, 5_000))
    }

    @Test
    fun historyRowIsDateDistanceDuration() {
        val epoch = Instant.parse("2026-09-23T00:00:00Z").toEpochMilli()
        assertEquals(
            "2026-09-23 · 1.00 km · 06:00",
            TrackStats.formatHistoryRow(epoch, 1000.0, 360_000, ZoneOffset.UTC),
        )
    }

    @Test
    fun hourDurationUsesHours() {
        assertEquals("1:01:01", TrackStats.formatDuration(3_661_000))
    }

    @Test
    fun weakAccuracyIsAboveFiftyMetersOrUnknown() {
        assertFalse(TrackingConfig.isWeakAccuracy(50f))
        assertTrue(TrackingConfig.isWeakAccuracy(50.1f))
        assertTrue(TrackingConfig.isWeakAccuracy(0f))
        assertFalse(TrackingConfig.isWeakAccuracy(8f))
    }
}
