package com.slafftrosheen.halomodellab

import android.content.Context
import com.slafftrosheen.halomodellab.catalog.CatalogRepository
import com.slafftrosheen.halomodellab.catalog.HaloInstalledCatalogRepository
import com.slafftrosheen.halomodellab.data.LabDatabase
import com.slafftrosheen.halomodellab.data.RecoveryReport
import com.slafftrosheen.halomodellab.data.RoomSessionRepository
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.data.RemoteEventImporter
import com.slafftrosheen.halomodellab.bridge.HaloBinderGateway
import com.slafftrosheen.halomodellab.export.ReportExporter
import com.slafftrosheen.halomodellab.runner.LabClock
import com.slafftrosheen.halomodellab.runner.SystemLabClock
import com.slafftrosheen.halomodellab.runner.HaloRemoteBenchmarkEngine
import com.slafftrosheen.halomodellab.runner.PersistedRealExecutionContextSource
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

    val clock: LabClock = SystemLabClock(context.applicationContext)

    val catalogRepository: CatalogRepository = CatalogRepository()

    /** Real inventory is queried from Halo only after a screen/user action requests it. */
    val haloGateway = HaloBinderGateway(context.applicationContext)
    val installedCatalogRepository = HaloInstalledCatalogRepository(haloGateway)

    private val database: LabDatabase = LabDatabase.build(context)

    val remoteEventImporter: RemoteEventImporter = RemoteEventImporter(database)

    val sessionRepository: SessionRepository = RoomSessionRepository(
        database = database,
        clock = clock,
        processInstanceId = processInstanceId,
    )

    /** Real runs use only the explicit Halo bridge and a context reconstructed from Room. */
    val haloRemoteBenchmarkEngine = HaloRemoteBenchmarkEngine(
        gateway = haloGateway,
        importer = remoteEventImporter,
        contexts = PersistedRealExecutionContextSource(sessionRepository),
        clientProcessInstanceId = processInstanceId,
        clientSourceId = UNVERIFIED_SOURCE_ID,
        clientSchemaSha256 = LAB_CLIENT_SCHEMA_SHA256,
        haloProtocolSchemaSha256 = HALO_PROTOCOL_SCHEMA_SHA256,
    )

    /** Writes only to private cache; sharing happens through the FileProvider afterwards. */
    val reportExporter: ReportExporter = ReportExporter(
        repository = sessionRepository,
        exportRoot = java.io.File(context.cacheDir, "exports"),
        clock = clock,
        packageName = context.packageName,
        appVersion = BuildConfig.VERSION_NAME,
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
        const val EXPORT_SCHEMA_VERSION: Int = 2

        /** Source exists for opt-in inventory IPC; runtime compatibility is not verified. */
        const val BRIDGE_STATUS: String = "SOURCE_PRESENT_UNVERIFIED"
        const val UNVERIFIED_SOURCE_ID: String = "not_recorded"
        const val LAB_CLIENT_SCHEMA_SHA256: String = "d866967284a236d37c39a720f7c13c46f39b1813f02f560c1857943727bcf231"
        const val HALO_PROTOCOL_SCHEMA_SHA256: String = "2ce1f13d156bedfa2de4e79232419a55172e298e12241e0d7800dc6c4fe5ab2c"
    }
}
