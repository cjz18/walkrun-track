package com.cjz18.walkrun.data

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.cjz18.walkrun.tracking.SessionPhase
import com.cjz18.walkrun.tracking.TrackPoint
import com.cjz18.walkrun.tracking.TrackStats
import com.cjz18.walkrun.tracking.TrackingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide session. [LocationTrackingService] appends fixes to [points] and the UI
 * reads the same flow, so a locked screen does not drop samples.
 */
class TrackRepository(
    private val database: AppDatabase,
    private val prefs: SharedPreferences,
) {
    private val dao = database.trackDao()

    private val _phase = MutableStateFlow(SessionPhase.Idle)
    val phase: StateFlow<SessionPhase> = _phase.asStateFlow()

    private val _points = MutableStateFlow<List<TrackPoint>>(emptyList())
    val points: StateFlow<List<TrackPoint>> = _points.asStateFlow()

    private val _weakGps = MutableStateFlow(false)
    val weakGps: StateFlow<Boolean> = _weakGps.asStateFlow()

    @Volatile
    var startedAtEpochMs: Long = 0L
        private set

    @Volatile
    var startedElapsedMs: Long = 0L
        private set

    @Volatile
    var endedAtEpochMs: Long = 0L
        private set

    @Volatile
    var frozenDurationMs: Long = 0L
        private set

    fun locationWasRequested(): Boolean = prefs.getBoolean(KEY_LOCATION_REQUESTED, false)

    fun markLocationRequested() {
        prefs.edit().putBoolean(KEY_LOCATION_REQUESTED, true).apply()
    }

    fun beginRecording(nowEpochMs: Long, nowElapsedMs: Long) {
        startedAtEpochMs = nowEpochMs
        startedElapsedMs = nowElapsedMs
        endedAtEpochMs = 0L
        frozenDurationMs = 0L
        _points.value = emptyList()
        _weakGps.value = false
        _phase.value = SessionPhase.Recording
    }

    fun append(point: TrackPoint) {
        if (_phase.value != SessionPhase.Recording) return
        _points.update { it + point }
        _weakGps.value = TrackingConfig.isWeakAccuracy(point.accuracyMeters)
    }

    fun markWeak() {
        if (_phase.value == SessionPhase.Recording) {
            _weakGps.value = true
        }
    }

    fun finishRecording(nowEpochMs: Long, nowElapsedMs: Long) {
        if (_phase.value != SessionPhase.Recording) return
        endedAtEpochMs = nowEpochMs
        frozenDurationMs = (nowElapsedMs - startedElapsedMs).coerceAtLeast(0L)
        _weakGps.value = false
        _phase.value = SessionPhase.Finished
    }

    fun discard() {
        _points.value = emptyList()
        _weakGps.value = false
        frozenDurationMs = 0L
        endedAtEpochMs = 0L
        _phase.value = SessionPhase.Idle
    }

    suspend fun saveCurrent(): Long {
        check(_phase.value == SessionPhase.Finished)
        val snapshot = _points.value
        val distance = TrackStats.distanceMeters(snapshot)
        val duration = frozenDurationMs
        val started = startedAtEpochMs
        val ended = endedAtEpochMs
        val id = database.withTransaction {
            val trackId = dao.insertTrack(
                TrackEntity(
                    startedAtEpochMs = started,
                    endedAtEpochMs = ended,
                    distanceMeters = distance,
                    durationMillis = duration,
                ),
            )
            if (snapshot.isNotEmpty()) {
                dao.insertPoints(
                    snapshot.map { point ->
                        TrackPointEntity(
                            trackId = trackId,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            timeEpochMs = point.timeEpochMs,
                            accuracyMeters = point.accuracyMeters,
                        )
                    },
                )
            }
            trackId
        }
        discard()
        return id
    }

    fun observeHistory(): Flow<List<TrackEntity>> = dao.observeTracks()

    suspend fun loadTrack(id: Long): SavedTrack? {
        val track = dao.track(id) ?: return null
        val points = dao.pointsFor(id).map { row ->
            TrackPoint(
                latitude = row.latitude,
                longitude = row.longitude,
                timeEpochMs = row.timeEpochMs,
                accuracyMeters = row.accuracyMeters,
            )
        }
        return SavedTrack(track, points)
    }

    data class SavedTrack(
        val track: TrackEntity,
        val points: List<TrackPoint>,
    )

    private companion object {
        const val KEY_LOCATION_REQUESTED = "location_permission_requested"
    }
}
