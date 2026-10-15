package nl.lloydstellar.spindoctor.export

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
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.lloydstellar.spindoctor.MainActivity
import nl.lloydstellar.spindoctor.R

/**
 * Keeps the app running while a release renders in the background, with a
 * progress notification. The work itself is done by [Exporter].
 * Android 15+: a media processing service; Android 12 to 14: data sync,
 * the type Android lists for processing local files.
 */
class ExportService : Service() {
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Video export", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while your videos are being made"
            }
        )
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        try {
            startForeground(ID, build(Exporter.state.value), type)
        } catch (e: Exception) {
            /* not allowed right now: the export still runs while the app is open */
            stopSelf()
            return START_NOT_STICKY
        }
        watcher?.cancel()
        watcher = CoroutineScope(Dispatchers.Main).launch {
            /* Android drops notification updates that come faster than a few per second */
            var last = 0L
            Exporter.state.collect { s ->
                val now = SystemClock.elapsedRealtime()
                if (s.running && now - last > 700) {
                    last = now
                    manager.notify(ID, build(s))
                }
            }
        }
        return START_NOT_STICKY
    }

    /* Android 15 caps media processing at six hours a day; a batch takes minutes, but be correct */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Exporter.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        watcher?.cancel()
        super.onDestroy()
    }

    private fun build(s: ExportState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (s.clipCount > 1) "Making video ${s.clipIndex + 1} of ${s.clipCount}" else "Making your video"
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(s.phase)
            .setProgress(1000, (s.progress * 1000).toInt(), s.frames == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "export"
        private const val ID = 33

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, ExportService::class.java))
            } catch (_: Exception) {
                /* the export does not depend on it */
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ExportService::class.java))
        }
    }
}
