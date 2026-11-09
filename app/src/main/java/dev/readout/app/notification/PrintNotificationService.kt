package dev.readout.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.readout.app.MainActivity
import dev.readout.app.R
import dev.readout.app.data.MachineState
import dev.readout.app.data.OctoPrintApi
import dev.readout.app.data.PrinterSnapshot
import dev.readout.app.data.PrinterStore
import dev.readout.app.data.SnapshotParser
import dev.readout.app.util.Format
import dev.readout.app.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps one ongoing notification and the home-screen widgets current while a print runs.
 *
 * The service owns its own lifecycle: it stops itself a few polls after the job leaves the
 * active states, which is also when the completion notification is posted. The app only asks
 * it to start (when a job becomes active) or to stop (when the user disables the setting).
 */
class PrintNotificationService : Service() {

    companion object {
        private const val ACTION_START = "dev.readout.app.action.PRINT_START"
        private const val ACTION_PAUSE = "dev.readout.app.action.PRINT_PAUSE"
        private const val ACTION_RESUME = "dev.readout.app.action.PRINT_RESUME"
        private const val ACTION_STOP = "dev.readout.app.action.PRINT_STOP"

        private const val NOTIFICATION_ID = 4101
        private const val EVENT_ID = 4102
        private const val POLL_MS = 5_000L

        private val ACTIVE = setOf(
            MachineState.Printing, MachineState.Paused, MachineState.Pausing,
            MachineState.Resuming, MachineState.Cancelling, MachineState.Finishing
        )

        fun start(context: Context) {
            val intent = Intent(context, PrintNotificationService::class.java).setAction(ACTION_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PrintNotificationService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var api: OctoPrintApi? = null
    private var lastNotificationKey: String? = null
    private var wasActive = false
    private var lastKnownJob: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        val config = PrinterStore(this).active()
        api = config?.let { OctoPrintApi(it.baseUrl, it.apiKey) }
        startForegroundCompat(buildStatusNotification(null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> scope.launch { runCatching { api?.pause() } }
            ACTION_RESUME -> scope.launch { runCatching { api?.resume() } }
        }

        if (loop?.isActive != true) {
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        val config = PrinterStore(this).active()
        if (config == null) {
            stopSelf()
            return
        }
        val client = OctoPrintApi(config.baseUrl, config.apiKey)
        api = client
        var idlePolls = 0
        var polls = 0

        while (scope.isActive) {
            val printer = runCatching { client.printer() }.getOrNull()
            val job = if (printer != null) runCatching { client.job() }.getOrNull() else null

            if (printer != null) {
                val snapshot = SnapshotParser.parse(printer, job, null)
                val active = snapshot.state in ACTIVE

                if (active) {
                    idlePolls = 0
                    wasActive = true
                    lastKnownJob = snapshot.jobName
                } else {
                    idlePolls++
                }

                WidgetRefresher.push(this, snapshot)

                // A still every ~60 s is enough for a wall-mounted glance; the status widgets
                // update on every poll.
                polls++
                if (polls % 12 == 1) {
                    WidgetRefresher.captureCamera(this, client, config)
                }

                val key = listOf(
                    snapshot.state, snapshot.jobName, snapshot.progress, snapshot.printTimeLeftSeconds
                ).toString()
                if (key != lastNotificationKey) {
                    lastNotificationKey = key
                    notifyStatus(buildStatusNotification(snapshot))
                }

                // The job left the active states: post the outcome once, then retire.
                if (wasActive && !active && idlePolls >= 2) {
                    postCompletion(snapshot)
                    stopSelf()
                    return
                }
                // Started outside a print and nothing appeared: do not linger.
                if (!wasActive && idlePolls >= 6) {
                    stopSelf()
                    return
                }
            }

            delay(POLL_MS)
        }
    }

    private fun buildStatusNotification(snapshot: PrinterSnapshot?): Notification {
        val state = snapshot?.state ?: MachineState.Unknown
        val percent = snapshot?.progress?.let { (it * 100).toInt() } ?: -1
        val title = when {
            snapshot == null -> "Watching the printer"
            percent >= 0 -> "$percent% · ${snapshot.stateLabel}"
            else -> snapshot.stateLabel
        }
        val text = snapshot?.jobName
            ?: "Readout is keeping the machine's state current."

        val builder = NotificationCompat.Builder(this, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_stat_readout)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (percent >= 0) {
            builder.setProgress(100, percent, false)
            snapshot?.printTimeLeftSeconds?.let { builder.setSubText("${Format.eta(it)} left") }
        } else if (snapshot != null && state in ACTIVE) {
            builder.setProgress(0, 0, true)
        }

        when (state) {
            MachineState.Printing -> builder.addAction(
                android.R.drawable.ic_media_pause, "Pause", serviceAction(ACTION_PAUSE, 11)
            )

            MachineState.Paused -> builder.addAction(
                android.R.drawable.ic_media_play, "Resume", serviceAction(ACTION_RESUME, 12)
            )

            else -> Unit
        }

        return builder.build()
    }

    private fun postCompletion(snapshot: PrinterSnapshot) {
        val finished = (snapshot.progress ?: 0f) >= 0.995f ||
            snapshot.state == MachineState.Finishing
        val title = if (finished) "Print finished" else "Print stopped"
        val text = snapshot.jobName ?: "The machine is no longer printing."

        val notification = NotificationCompat.Builder(this, NotificationChannels.EVENTS)
            .setSmallIcon(R.drawable.ic_stat_readout)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        runCatching { NotificationManagerCompat.from(this).notify(EVENT_ID, notification) }
    }

    private fun notifyStatus(notification: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PrintNotificationService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
