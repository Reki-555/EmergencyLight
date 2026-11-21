package com.emergencylight.app

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the flashlight and the SOS siren working when the
 * screen is off, the user goes to the home screen, or the app is swiped away.
 *
 * - Runs only while the flashlight is on OR SOS is sounding; stops itself when both are off.
 * - Foreground type: mediaPlayback during SOS, specialUse while only the flashlight is on.
 * - Holds a partial wake lock only during SOS (so the siren and Morse timing keep running).
 * - The real work (siren, torch, Morse) is done by AppState; this service just keeps the
 *   app process alive and shows the ongoing notification with its buttons.
 */
class LightService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var stoppingOnPurpose = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppState.init(applicationContext)
        ensureChannel(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        stoppingOnPurpose = false
        // Must enter the foreground quickly after startForegroundService().
        goForeground()
        when (intent?.action) {
            ACTION_STOP_SOS -> AppState.stopSos(fromApp = false)
            ACTION_TORCH_OFF -> AppState.setTorch(false, fromApp = false)
        }
        refresh()
        return START_NOT_STICKY
    }

    /** Updates notification / type / wake lock, or stops the service when nothing is running. */
    fun refresh() {
        val needed = AppState.torchOn || AppState.sosActive
        if (!needed) {
            stopNow()
            return
        }
        goForeground()
        if (AppState.sosActive) acquireWakeLock() else releaseWakeLock()
    }

    private fun goForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val type = if (AppState.sosActive) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Never crash: the light/siren still work while the app is open.
        }
    }

    private fun stopNow() {
        stoppingOnPurpose = true
        releaseWakeLock()
        if (instance === this) instance = null
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // ignore
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from Recents: keep running (do nothing).
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        if (instance === this) instance = null
        if (!stoppingOnPurpose) {
            // Killed by the system (e.g. battery saver): show the true state next time.
            AppState.onServiceKilledBySystem()
        }
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EmergencyLight:sos").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // safety limit 12 h; released at STOP
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(this, REQ_OPEN, openAppIntent(this), flags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (AppState.sosActive) {
            // With "Hold STOP for 2 seconds" on, the button only opens the app.
            val stopIntent = if (AppState.holdToStop) {
                PendingIntent.getActivity(this, REQ_OPEN_FOR_STOP, openAppIntent(this), flags)
            } else {
                PendingIntent.getService(
                    this, REQ_STOP_SOS,
                    Intent(this, LightService::class.java).setAction(ACTION_STOP_SOS), flags
                )
            }
            builder.setContentText(getString(R.string.notif_sos))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .addAction(0, getString(R.string.notif_stop_alarm), stopIntent)
        } else {
            val torchOff = PendingIntent.getService(
                this, REQ_TORCH_OFF,
                Intent(this, LightService::class.java).setAction(ACTION_TORCH_OFF), flags
            )
            builder.setContentText(getString(R.string.notif_flashlight_on))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, getString(R.string.notif_turn_off_flashlight), torchOff)
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "status"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP_SOS = "com.emergencylight.app.action.STOP_SOS"
        private const val ACTION_TORCH_OFF = "com.emergencylight.app.action.TORCH_OFF"
        private const val REQ_OPEN = 10
        private const val REQ_OPEN_FOR_STOP = 11
        private const val REQ_STOP_SOS = 12
        private const val REQ_TORCH_OFF = 13

        @Volatile
        var instance: LightService? = null
            private set

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LightService::class.java))
            } catch (e: Exception) {
                // e.g. not allowed from the background: the app keeps working while open.
            }
        }

        fun openAppIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Silent channel: no sound, no vibration. */
        fun ensureChannel(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) != null) return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
