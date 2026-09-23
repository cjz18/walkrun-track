package com.cjz18.walkrun.ui

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cjz18.walkrun.WalkRunApp
import com.cjz18.walkrun.data.TrackRepository
import com.cjz18.walkrun.tracking.LocationTrackingService
import com.cjz18.walkrun.tracking.SessionPhase
import com.cjz18.walkrun.tracking.TrackPoint
import com.cjz18.walkrun.tracking.TrackStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RecorderUi(
    val phase: SessionPhase = SessionPhase.Idle,
    val points: List<TrackPoint> = emptyList(),
    val weakGps: Boolean = false,
    val statsLine: String = TrackStats.formatStats(0.0, 0L),
    val sessionKey: Long = 0L,
)

data class HistoryRow(
    val id: Long,
    val line: String,
)

data class ReplayUi(
    val statsLine: String,
    val points: List<TrackPoint>,
)

sealed interface Destination {
    data object Recorder : Destination
    data object History : Destination
    data object Replay : Destination
}

class TrackViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as WalkRunApp).repository

    private val tick = MutableStateFlow(0L)
    private val _destination = MutableStateFlow<Destination>(Destination.Recorder)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    private val _replay = MutableStateFlow<ReplayUi?>(null)
    val replay: StateFlow<ReplayUi?> = _replay.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _locationRequested = MutableStateFlow(repository.locationWasRequested())
    val locationRequested: StateFlow<Boolean> = _locationRequested.asStateFlow()

    val recorder: StateFlow<RecorderUi> = combine(
        repository.phase,
        repository.points,
        repository.weakGps,
        tick,
    ) { phase, points, weak, _ ->
        val duration = when (phase) {
            SessionPhase.Recording ->
                (SystemClock.elapsedRealtime() - repository.startedElapsedMs).coerceAtLeast(0L)
            SessionPhase.Finished -> repository.frozenDurationMs
            SessionPhase.Idle -> 0L
        }
        val distance = if (phase == SessionPhase.Idle) 0.0 else TrackStats.distanceMeters(points)
        RecorderUi(
            phase = phase,
            points = if (phase == SessionPhase.Idle) emptyList() else points,
            weakGps = phase == SessionPhase.Recording && weak,
            statsLine = TrackStats.formatStats(distance, duration),
            sessionKey = repository.startedAtEpochMs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecorderUi())

    val history: StateFlow<List<HistoryRow>> = repository.observeHistory()
        .map { tracks ->
            tracks.map { track ->
                HistoryRow(
                    id = track.id,
                    line = TrackStats.formatHistoryRow(
                        startedAtEpochMs = track.startedAtEpochMs,
                        distanceMeters = track.distanceMeters,
                        durationMillis = track.durationMillis,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            while (isActive) {
                if (repository.phase.value == SessionPhase.Recording) {
                    tick.value = SystemClock.elapsedRealtime()
                }
                delay(1_000)
            }
        }
    }

    fun markLocationRequested() {
        repository.markLocationRequested()
        _locationRequested.value = true
    }

    fun startRecording() {
        if (repository.phase.value == SessionPhase.Recording) return
        repository.beginRecording(
            nowEpochMs = System.currentTimeMillis(),
            nowElapsedMs = SystemClock.elapsedRealtime(),
        )
        val intent = Intent(getApplication(), LocationTrackingService::class.java)
            .setAction(LocationTrackingService.ACTION_START)
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun endRecording() {
        if (repository.phase.value != SessionPhase.Recording) return
        repository.finishRecording(
            nowEpochMs = System.currentTimeMillis(),
            nowElapsedMs = SystemClock.elapsedRealtime(),
        )
        val intent = Intent(getApplication(), LocationTrackingService::class.java)
            .setAction(LocationTrackingService.ACTION_STOP)
        getApplication<Application>().startService(intent)
    }

    fun discard() {
        if (repository.phase.value == SessionPhase.Recording) {
            endRecording()
        }
        repository.discard()
        _destination.value = Destination.Recorder
    }

    fun saveToHistory() {
        if (_saving.value || repository.phase.value != SessionPhase.Finished) return
        viewModelScope.launch {
            _saving.value = true
            try {
                repository.saveCurrent()
                _destination.value = Destination.History
            } finally {
                _saving.value = false
            }
        }
    }

    fun openHistory() {
        _destination.value = Destination.History
    }

    fun closeHistory() {
        _replay.value = null
        _destination.value = Destination.Recorder
    }

    fun openReplay(id: Long) {
        viewModelScope.launch {
            val saved = repository.loadTrack(id) ?: return@launch
            _replay.value = ReplayUi(
                statsLine = TrackStats.formatStats(
                    saved.track.distanceMeters,
                    saved.track.durationMillis,
                ),
                points = saved.points,
            )
            _destination.value = Destination.Replay
        }
    }

    fun closeReplay() {
        _replay.value = null
        _destination.value = Destination.History
    }
}
