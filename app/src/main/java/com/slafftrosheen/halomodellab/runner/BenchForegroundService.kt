package com.slafftrosheen.halomodellab.runner

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.slafftrosheen.halomodellab.AppContainer
import com.slafftrosheen.halomodellab.LabApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground host for [SessionCoordinator].
 *
 * Started only by an explicit user action, never by boot or a periodic trigger, and
 * START_NOT_STICKY so the platform does not silently resurrect a stopped queue. Promotion to
 * foreground happens before any session work. If the session cannot be claimed (for example
 * because it is already terminal), the service stops instead of leaving it falsely RUNNING.
 */
class BenchForegroundService : Service() {

    // Main-confined orchestration: runJob and pendingStart are only touched from
    // onStartCommand and the join continuation. The coordinator still launches the queue
    // on its own dispatcher, so queue work never runs on the main thread.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var container: AppContainer
    private lateinit var notifications: BenchNotifications
    private lateinit var coordinator: SessionCoordinator

    private var runJob: Job? = null
    private var pendingStartSessionId: String? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as LabApplication).container
        notifications = BenchNotifications(this)
        notifications.ensureChannel()
        coordinator = SessionCoordinator(
            repository = container.sessionRepository,
            engine = FakeBenchmarkEngine(),
            conditions = AndroidDeviceConditions(this),
            processInstanceId = container.processInstanceId,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                promoteToForeground()
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (sessionId == null) {
                    pendingStartSessionId = null
                    stopRunner()
                } else {
                    startRun(sessionId)
                }
            }

            ACTION_PAUSE -> {
                pendingStartSessionId = null
                coordinator.requestPause()
                if (runJob?.isActive != true) stopRunner()
            }

            ACTION_STOP -> {
                pendingStartSessionId = null
                coordinator.requestStop(REASON_USER_STOP)
                if (runJob?.isActive != true) stopRunner()
            }

            else -> stopRunner()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            BenchNotifications.NOTIFICATION_ID,
            notifications.runningNotification(),
            serviceType,
        )
    }

    private fun startRun(sessionId: String) {
        if (runJob?.isActive == true) {
            // The previous run may be finishing right as the session became PAUSED and the
            // user pressed Start again. Dropping this intent would leave the session idle,
            // so remember it and start once the current job has fully released the queue.
            pendingStartSessionId = sessionId
            return
        }

        val job = coordinator.start(serviceScope, sessionId)
        runJob = job
        serviceScope.launch {
            job.join()
            // A newer job may already have replaced this one; only the current job may
            // clear state or stop the service.
            if (runJob !== job) return@launch
            runJob = null
            val pending = pendingStartSessionId
            pendingStartSessionId = null
            if (pending != null) {
                startRun(pending)
            } else {
                stopRunner()
            }
        }
    }

    private fun stopRunner() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START: String = "com.slafftrosheen.halomodellab.action.START"
        const val ACTION_PAUSE: String = "com.slafftrosheen.halomodellab.action.PAUSE"
        const val ACTION_STOP: String = "com.slafftrosheen.halomodellab.action.STOP"
        const val EXTRA_SESSION_ID: String = "sessionId"
        const val REASON_USER_STOP: String = "USER_STOP"

        fun start(context: Context, sessionId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BenchForegroundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SESSION_ID, sessionId),
            )
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, BenchForegroundService::class.java).setAction(ACTION_PAUSE),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BenchForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
