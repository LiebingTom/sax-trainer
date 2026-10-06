package com.example.saxtrainer

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.saxtrainer.core.reed.ReedCatalog
import com.example.saxtrainer.core.reed.ReedLogbook

/**
 * The running reed session, as an ongoing notification: which reed, how long, and Stop.
 *
 * The service keeps no time of its own. Everything it shows comes from the stored session - the
 * clock is the system's chronometer counting from the session's start - so it can be killed and
 * started again at any point and still show the right thing. It only stops a session through
 * [AppContainer.reedSessions], the same way the logbook and the landing page do.
 *
 * Started and stopped by [ReedSessionSync], never by a screen.
 */
class ReedSessionService : Service() {

    private val container: AppContainer get() = (application as SaxTrainerApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            container.reedSessions.stop()
        }
        ensureChannel()
        val notification = notificationFor(container.reedLogbook.logbook.value)
        if (notification == null) {
            // Stopped in the moment between being asked to show and showing. A service started as a
            // foreground one has to show something before it may go, so it does - and goes.
            showInForeground(endedNotification())
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        showInForeground(notification)
        // Not brought back by the system on its own: the session is stored, and the next time the
        // app is opened the sync starts this again from it.
        return START_NOT_STICKY
    }

    /** As a foreground service, of the special-use type where Android asks for a type at all. */
    private fun showInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun endedNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("Reed session ended")
        .build()

    /** What the notification says for [logbook], or null when nothing is running. */
    private fun notificationFor(logbook: ReedLogbook): Notification? {
        val session = logbook.runningSession ?: return null
        val reed = logbook.reedOf(session.reedId) ?: return null

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_REED_LOGBOOK, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ReedSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Reed No. ${reed.number}")
            .setContentText(ReedCatalog.describe(reed))
            .setWhen(session.start.toEpochMilliseconds())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reed session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The reed being played and for how long, while a session runs."
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "reed_session"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.example.saxtrainer.action.STOP_REED_SESSION"

        /** Shows the notification for what is running now, or takes it away. */
        fun refresh(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReedSessionService::class.java))
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, ReedSessionService::class.java))
        }
    }
}
