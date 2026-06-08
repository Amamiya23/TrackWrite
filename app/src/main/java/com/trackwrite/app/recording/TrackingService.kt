package com.trackwrite.app.recording

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trackwrite.app.MainActivity
import com.trackwrite.app.R
import com.trackwrite.app.data.TrackRepository
import com.trackwrite.app.domain.GeoPoint
import com.trackwrite.app.domain.TrackPoint
import com.trackwrite.app.settings.AppSettingsStore
import java.time.Instant

class TrackingService : Service(), LocationListener {
    private lateinit var repository: TrackRepository
    private lateinit var stateStore: RecordingStateStore
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var locationManager: LocationManager
    private var pointCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        repository = TrackRepository(this)
        stateStore = RecordingStateStore(this)
        settingsStore = AppSettingsStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent.getStringExtra(EXTRA_TRACK_NAME).orEmpty())
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            else -> recoverIfNeeded()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        val state = stateStore.current()
        if (state.status != RecordingStatus.Recording || state.trackId == null) return
        if (!repository.trackExists(state.trackId)) {
            stopUnavailableRecording()
            return
        }
        val recordedAtMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        try {
            repository.appendPoint(
                trackId = state.trackId,
                point = TrackPoint(
                    position = GeoPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                    ),
                    recordedAt = Instant.ofEpochMilli(recordedAtMillis),
                ),
            )
        } catch (_: RuntimeException) {
            stopUnavailableRecording()
            return
        }
        pointCount += 1
        stateStore.set(
            trackId = state.trackId,
            status = RecordingStatus.Recording,
            lastPointRecordedAtMillis = recordedAtMillis,
            provider = location.provider,
            issue = RecordingIssue.None,
        )
        updateNotification("Recording", "Captured $pointCount points")
    }

    @Deprecated("Deprecated by Android framework")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun startRecording(name: String) {
        val current = stateStore.current()
        if (current.trackId != null &&
            current.status != RecordingStatus.Stopped &&
            repository.trackExists(current.trackId)
        ) {
            pointCount = repository.countPoints(current.trackId)
            when (current.status) {
                RecordingStatus.Recording -> {
                    startForeground(NOTIFICATION_ID, notification("Recording", "Recovered recording"))
                    requestLocationUpdates()
                }
                RecordingStatus.Paused -> {
                    startForeground(NOTIFICATION_ID, notification("Paused", "Recording is paused"))
                    locationManager.removeUpdates(this)
                }
                RecordingStatus.Stopped -> Unit
            }
            return
        }

        val track = repository.createTrack(name.ifBlank { "Recording ${Instant.now()}" })
        pointCount = 0
        stateStore.set(
            trackId = track.id,
            status = RecordingStatus.Recording,
            lastPointRecordedAtMillis = null,
            provider = null,
            issue = RecordingIssue.WaitingForFix,
        )
        startForeground(NOTIFICATION_ID, notification("Recording", "Waiting for GPS fix"))
        requestLocationUpdates()
    }

    private fun pauseRecording() {
        val current = stateStore.current()
        stateStore.set(current.trackId, RecordingStatus.Paused, issue = RecordingIssue.None)
        locationManager.removeUpdates(this)
        updateNotification("Paused", "Recording is paused")
    }

    private fun resumeRecording() {
        val current = stateStore.current()
        if (current.trackId == null) return
        if (!repository.trackExists(current.trackId)) {
            stopUnavailableRecording()
            return
        }
        pointCount = repository.countPoints(current.trackId)
        stateStore.set(current.trackId, RecordingStatus.Recording, issue = RecordingIssue.WaitingForFix)
        startForeground(NOTIFICATION_ID, notification("Recording", "Waiting for GPS fix"))
        requestLocationUpdates()
    }

    private fun stopRecording() {
        stateStore.set(
            trackId = null,
            status = RecordingStatus.Stopped,
            lastPointRecordedAtMillis = null,
            provider = null,
            issue = RecordingIssue.None,
        )
        locationManager.removeUpdates(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun recoverIfNeeded() {
        val current = stateStore.current()
        if (current.status == RecordingStatus.Recording && current.trackId != null) {
            if (!repository.trackExists(current.trackId)) {
                stopUnavailableRecording()
                return
            }
            pointCount = repository.countPoints(current.trackId)
            startForeground(NOTIFICATION_ID, notification("Recovered recording", "Waiting for GPS fix"))
            requestLocationUpdates()
        }
    }

    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            val current = stateStore.current()
            stateStore.set(current.trackId, current.status, issue = RecordingIssue.PermissionMissing)
            updateNotification("Permission required", "Open TrackWrite and grant location permission")
            return
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            val current = stateStore.current()
            stateStore.set(current.trackId, current.status, provider = null, issue = RecordingIssue.LocationDisabled)
            updateNotification("Location disabled", "Enable device location services")
            return
        }

        val frequency = settingsStore.current().recordingFrequency
        val current = stateStore.current()
        stateStore.set(
            trackId = current.trackId,
            status = current.status,
            provider = provider,
            issue = if (current.lastPointRecordedAtMillis == null) RecordingIssue.WaitingForFix else RecordingIssue.None,
        )
        locationManager.removeUpdates(this)
        locationManager.requestLocationUpdates(provider, frequency.intervalMs, frequency.distanceMeters, this)
    }

    private fun stopUnavailableRecording() {
        pointCount = 0
        stateStore.set(
            trackId = null,
            status = RecordingStatus.Stopped,
            lastPointRecordedAtMillis = null,
            provider = null,
            issue = RecordingIssue.None,
        )
        locationManager.removeUpdates(this)
        startForeground(
            NOTIFICATION_ID,
            notification(
                getString(R.string.recording_stopped_title),
                getString(R.string.recording_track_unavailable),
            ),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trackwrite)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(title, text))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Track recording", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_TRACK_NAME = "track_name"

        const val ACTION_START = "com.trackwrite.app.recording.START"
        const val ACTION_PAUSE = "com.trackwrite.app.recording.PAUSE"
        const val ACTION_RESUME = "com.trackwrite.app.recording.RESUME"
        const val ACTION_STOP = "com.trackwrite.app.recording.STOP"

        fun command(context: Context, action: String, trackName: String? = null): Intent =
            Intent(context, TrackingService::class.java)
                .setAction(action)
                .putExtra(EXTRA_TRACK_NAME, trackName)
    }
}
