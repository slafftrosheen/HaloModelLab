package com.slafftrosheen.halomodellab

import android.content.Context
import com.slafftrosheen.halomodellab.catalog.CatalogRepository
import com.slafftrosheen.halomodellab.data.LabDatabase
import com.slafftrosheen.halomodellab.data.RecoveryReport
import com.slafftrosheen.halomodellab.data.RoomSessionRepository
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.runner.LabClock
import com.slafftrosheen.halomodellab.runner.SystemLabClock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Startup gate: no start command may be accepted before recovery has run. */
sealed interface StartupState {
    data object Recovering : StartupState
    data class Ready(val report: RecoveryReport) : StartupState
    data class Failed(val detail: String) : StartupState
}

/**
 * Small manual dependency container (constructor injection only, no service locator).
 *
 * The Application owns exactly one instance, so the UI and the foreground service share one
 * repository and one database. Recovery runs once per process, before start commands are
 * accepted. Nothing else starts here: no service, no runner, no boot receiver.
 */
class AppContainer(context: Context) {

    val processInstanceId: String = UUID.randomUUID().toString()

    val clock: LabClock = SystemLabClock()

    val catalogRepository: CatalogRepository = CatalogRepository()

    private val database: LabDatabase = LabDatabase.build(context)

    val sessionRepository: SessionRepository = RoomSessionRepository(
        database = database,
        clock = clock,
        processInstanceId = processInstanceId,
    )

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _startupState = MutableStateFlow<StartupState>(StartupState.Recovering)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    init {
        startupScope.launch {
            val next = try {
                StartupState.Ready(sessionRepository.recoverInterruptedOwner(processInstanceId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                StartupState.Failed(failure.message ?: failure::class.java.simpleName)
            }
            _startupState.value = next
        }
    }

    suspend fun awaitReady(): StartupState = startupState.first { it !is StartupState.Recovering }

    companion object {
        /** Report protocol version for future exports. */
        const val PROTOCOL_VERSION: Int = 1

        /** Export schemaVersion for future archives. */
        const val EXPORT_SCHEMA_VERSION: Int = 1

        /** Honest v0 bridge state; see SPEC §11. */
        const val BRIDGE_STATUS: String = "NOT_IMPLEMENTED"
    }
}
