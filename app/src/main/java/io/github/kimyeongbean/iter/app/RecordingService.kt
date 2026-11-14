package io.github.kimyeongbean.iter.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

private const val TAG = "RecordingService"
private const val CHANNEL_ID = "recording"
private const val NOTIFICATION_ID = 1

/**
 * Recording service (GLOSSARY: 기록 서비스, #125): a foreground service that keeps the process
 * alive so drive sessions are recorded without the screen, after a boot and after the app is
 * swiped away from recents. It only starts the drive session recorder; the recorder itself
 * decides when a drive session starts and ends.
 *
 * Runs as a location service when it may (screen open, or background location allowed);
 * otherwise as a special use service, and the recorder records without location (#124: Android 14
 * refuses a location service started in the background without background location).
 */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        (application as IterApplication).startRecording()
        // Restarted by the system if the process is killed for memory.
        return START_STICKY
    }

    private fun goForeground(): Boolean {
        val notification = notification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
            return true
        }
        val types =
            buildList {
                add(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        for (type in types) {
            val started = runCatching { startForeground(NOTIFICATION_ID, notification, type) }
            if (started.isSuccess) {
                Log.i(TAG, "foreground as type $type")
                return true
            }
            Log.w(TAG, "foreground as type $type refused", started.exceptionOrNull())
        }
        return false
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        /** Starts the recording service; a second start while it runs does nothing new. */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, RecordingService::class.java)) }
                .onFailure { Log.w(TAG, "start refused", it) }
        }
    }
}
