package com.cjz18.walkrun.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.cjz18.walkrun.MainActivity
import com.cjz18.walkrun.R
import com.cjz18.walkrun.WalkRunApp
import com.cjz18.walkrun.data.TrackRepository

/**
 * Foreground location service. Samples keep appending to [TrackRepository.points]
 * while the activity is stopped or the screen is locked.
 */
class LocationTrackingService : Service() {
    private val repository: TrackRepository
        get() = (application as WalkRunApp).repository

    private var client: AMapLocationClient? = null
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stop = intent?.action == ACTION_STOP || repository.phase.value != SessionPhase.Recording
        if (stop) {
            if (intent?.action != ACTION_STOP) {
                // Sticky restart with no live session still has to call startForeground.
                enterForeground()
            }
            leaveForeground()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground()) return START_NOT_STICKY
        startClient()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseClient()
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        if (inForeground) return true
        ensureChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            inForeground = true
            return true
        } catch (security: SecurityException) {
            Log.e(TAG, "startForeground failed", security)
            repository.discard()
            stopSelf()
            return false
        }
    }

    private fun startClient() {
        if (client != null || !inForeground) return
        try {
            val locationClient = AMapLocationClient(applicationContext)
            val option = AMapLocationClientOption()
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
            option.setInterval(TrackingConfig.SAMPLE_INTERVAL_MS)
            option.setNeedAddress(false)
            option.setOnceLocation(false)
            locationClient.setLocationOption(option)
            locationClient.setLocationListener { location -> onLocation(location) }
            locationClient.startLocation()
            client = locationClient
        } catch (error: Exception) {
            Log.e(TAG, "AMapLocationClient failed", error)
            repository.markWeak()
        }
    }

    private fun onLocation(location: AMapLocation?) {
        if (location == null || location.errorCode != 0) {
            if (location != null) {
                Log.w(TAG, "location error ${location.errorCode}")
            }
            repository.markWeak()
            return
        }
        repository.append(
            TrackPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                timeEpochMs = location.time,
                accuracyMeters = location.accuracy,
            ),
        )
    }

    private fun leaveForeground() {
        releaseClient()
        if (inForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
    }

    private fun releaseClient() {
        client?.let { locationClient ->
            locationClient.stopLocation()
            locationClient.onDestroy()
        }
        client = null
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_START = "com.cjz18.walkrun.action.START_TRACK"
        const val ACTION_STOP = "com.cjz18.walkrun.action.STOP_TRACK"
        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "LocationTracking"
    }
}
