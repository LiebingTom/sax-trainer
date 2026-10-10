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
import com.example.saxtrainer.core.audio.metronome.ActiveMetronome
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The open metronome, as an ongoing notification: its tempo, whether it is playing, and Start or
 * Stop - and, as a media-playback foreground service, what lets it keep playing with the app in the
 * background and the phone locked, the way a song does.
 *
 * It owns no click. The metronome belongs to its screen and is reached through
 * [AppContainer.activeMetronome]; this only shows it and toggles it. Whenever this goes - the task
 * swiped away, the system stopping it - the click stops with it, so it never plays on unseen.
 *
 * Started and stopped by [MetronomeSync], never by a screen.
 */
class MetronomeService : Service() {

    private val active: ActiveMetronome
        get() = (application as SaxTrainerApplication).container.activeMetronome

    private val scope = MainScope()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Kept up to date from the metronome itself, so a tempo changed or a Stop pressed on the
        // screen shows here too. The first value is the one onStartCommand already shows.
        scope.launch {
            active.summary.drop(1).collect { summary ->
                if (summary == null) {
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notificationFor(summary))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) active.toggle()
        ensureChannel()
        val summary = active.currentSummary
        if (summary == null) {
            // Closed in the moment between being asked to show and showing. A service started as a
            // foreground one has to show something before it may go, so it does - and goes.
            showInForeground(closedNotification())
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        showInForeground(notificationFor(summary))
        isShowing = true
        // Never brought back by the system: with no screen, there is no metronome to show.
        return START_NOT_STICKY
    }

    /** The app swiped away from the recent apps: the metronome goes with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        active.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isShowing = false
        scope.cancel()
        // No notification, no playing in the background.
        active.stop()
        super.onDestroy()
    }

    private fun showInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun closedNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("Metronome closed")
        .build()

    private fun notificationFor(summary: ActiveMetronome.Summary): Notification {
        val open = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_METRONOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this,
            REQUEST_TOGGLE,
            Intent(this, MetronomeService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val status = if (summary.isRunning) "Playing" else "Stopped"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Metronome · ${summary.bpm} bpm")
            .setContentText("$status · ${summary.beatsPerBar} beats a bar")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            // Start and Stop on the lock screen, where the player is when they need them.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, if (summary.isRunning) "Stop" else "Start", toggle)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            // Default importance so it shows on the lock screen, as the reed session's does - but
            // silent: the click is the only sound the metronome makes.
            NotificationChannel(CHANNEL_ID, "Metronome", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The open metronome, with Start and Stop, while it can play in the background."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "metronome"

        /** Its own, beside the reed session's 1: the two are shown at the same time. */
        private const val NOTIFICATION_ID = 2
        private const val REQUEST_OPEN = 0
        private const val REQUEST_TOGGLE = 1
        private const val ACTION_TOGGLE = "com.example.saxtrainer.action.TOGGLE_METRONOME"

        /** Whether the notification is up - the one condition for the click to play in the background. */
        @Volatile
        var isShowing: Boolean = false
            private set

        fun show(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MetronomeService::class.java))
        }

        fun dismiss(context: Context) {
            context.stopService(Intent(context, MetronomeService::class.java))
        }
    }
}
