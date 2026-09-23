package com.cjz18.walkrun.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cjz18.walkrun.R
import com.cjz18.walkrun.tracking.SessionPhase

private val WeakHint = Color(0xFF9E9E9E)
private val Panel = Color.White.copy(alpha = 0.92f)

@Composable
fun WalkRunScreen(viewModel: TrackViewModel = viewModel()) {
    val destination by viewModel.destination.collectAsState()
    val recorder by viewModel.recorder.collectAsState()
    val history by viewModel.history.collectAsState()
    val replay by viewModel.replay.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val locationRequested by viewModel.locationRequested.collectAsState()
    val context = LocalContext.current
    var locationGranted by remember { mutableStateOf(context.hasLocationPermission()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationGranted = context.hasLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = context.hasLocationPermission() || granted
        if (locationGranted) {
            viewModel.startRecording()
        }
    }

    val showDenied = !locationGranted && locationRequested
    val blockBack = destination != Destination.Recorder ||
        recorder.phase == SessionPhase.Recording ||
        recorder.phase == SessionPhase.Finished
    BackHandler(enabled = blockBack) {
        when (destination) {
            Destination.Replay -> viewModel.closeReplay()
            Destination.History -> viewModel.closeHistory()
            Destination.Recorder -> Unit
        }
    }

    when (destination) {
        Destination.History -> HistoryScreen(
            rows = history,
            onBack = viewModel::closeHistory,
            onOpen = viewModel::openReplay,
        )
        Destination.Replay -> ReplayScreen(
            replay = replay,
            onBack = viewModel::closeReplay,
        )
        Destination.Recorder -> RecorderScreen(
            recorder = recorder,
            showDenied = showDenied,
            saving = saving,
            onStart = {
                viewModel.markLocationRequested()
                if (context.hasLocationPermission()) {
                    locationGranted = true
                    if (context.needsNotificationPermission()) {
                        permissionLauncher.launch(context.permissionsForStart())
                    }
                    viewModel.startRecording()
                } else {
                    permissionLauncher.launch(context.permissionsForStart())
                }
            },
            onHistory = viewModel::openHistory,
            onSettings = { context.openAppSettings() },
            onEnd = viewModel::endRecording,
            onSave = viewModel::saveToHistory,
            onDiscard = viewModel::discard,
        )
    }
}

@Composable
private fun RecorderScreen(
    recorder: RecorderUi,
    showDenied: Boolean,
    saving: Boolean,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onEnd: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val mode = when (recorder.phase) {
        SessionPhase.Idle -> MapMode.Idle
        SessionPhase.Recording -> MapMode.Follow
        SessionPhase.Finished -> MapMode.Fit
    }
    val logoBottomDp = when (recorder.phase) {
        SessionPhase.Idle -> 220
        SessionPhase.Recording -> 140
        SessionPhase.Finished -> 180
    }
    Box(Modifier.fillMaxSize()) {
        TrackMap(
            points = recorder.points,
            mode = mode,
            sessionKey = recorder.sessionKey,
            logoBottomDp = logoBottomDp,
            modifier = Modifier.fillMaxSize(),
        )
        when (recorder.phase) {
            SessionPhase.Idle -> IdleOverlay(
                showDenied = showDenied,
                onStart = onStart,
                onHistory = onHistory,
                onSettings = onSettings,
            )
            SessionPhase.Recording -> {
                StatsBar(
                    line = recorder.statsLine,
                    weakGps = recorder.weakGps,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                BottomPanel(Modifier.align(Alignment.BottomCenter)) {
                    Button(
                        onClick = onEnd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.end), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            SessionPhase.Finished -> {
                StatsBar(
                    line = recorder.statsLine,
                    weakGps = false,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                BottomPanel(Modifier.align(Alignment.BottomCenter)) {
                    Button(
                        onClick = onSave,
                        enabled = !saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        Text(stringResource(R.string.save), style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = onDiscard, enabled = !saving) {
                        Text(stringResource(R.string.discard))
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleOverlay(
    showDenied: Boolean,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        TextButton(
            onClick = onHistory,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Panel, MaterialTheme.shapes.small),
        ) {
            Text(stringResource(R.string.history))
        }
        BottomPanel(Modifier.align(Alignment.BottomCenter)) {
            Text(
                text = stringResource(if (showDenied) R.string.permission_denied else R.string.hint_start),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showDenied) {
                TextButton(onClick = onSettings) {
                    Text(stringResource(R.string.go_settings))
                }
            }
            Button(
                onClick = onStart,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .height(72.dp)
                    .widthIn(min = 200.dp),
            ) {
                Text(stringResource(R.string.start), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun StatsBar(
    line: String,
    weakGps: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp)
            .background(Panel, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (weakGps) {
            Text(
                text = stringResource(R.string.weak_gps),
                color = WeakHint,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BottomPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Panel)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun HistoryScreen(
    rows: List<HistoryRow>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
            Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge)
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = WeakHint)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(rows, key = { it.id }) { row ->
                    Text(
                        text = row.line,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(row.id) }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE6E6E6)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplayScreen(
    replay: ReplayUi?,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        TrackMap(
            points = replay?.points.orEmpty(),
            mode = MapMode.Fit,
            sessionKey = replay?.points?.firstOrNull()?.timeEpochMs ?: 0L,
            logoBottomDp = 72,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.align(Alignment.TopCenter)) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 4.dp)
                    .background(Panel, MaterialTheme.shapes.small),
            ) {
                Text(stringResource(R.string.back))
            }
            if (replay != null) {
                StatsBar(line = replay.statsLine, weakGps = false)
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}

private fun Context.needsNotificationPermission(): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
    ) != PackageManager.PERMISSION_GRANTED
}

private fun Context.permissionsForStart(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    if (needsNotificationPermission()) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}
