package com.slafftrosheen.halomodellab.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.catalog.CatalogMode
import com.slafftrosheen.halomodellab.catalog.CatalogRepository
import com.slafftrosheen.halomodellab.catalog.CatalogState
import com.slafftrosheen.halomodellab.catalog.HaloInstalledCatalogRepository
import com.slafftrosheen.halomodellab.catalog.InstalledCatalogResult
import com.slafftrosheen.halomodellab.catalog.RealCatalogStatus
import com.slafftrosheen.halomodellab.catalog.InstalledModelDescriptor
import com.slafftrosheen.halomodellab.catalog.FastSmokePlanFactory
import com.slafftrosheen.halomodellab.catalog.FastSmokePlanResult
import com.slafftrosheen.halomodellab.data.CreateSessionResult
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.bridge.BridgeConsentScope
import com.slafftrosheen.halomodellab.bridge.BridgeFeature
import com.slafftrosheen.halomodellab.bridge.BridgeHandshakeRequest
import com.slafftrosheen.halomodellab.bridge.BridgeProtocolVersion
import com.slafftrosheen.halomodellab.bridge.HaloLabGateway
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.RealBackend
import java.util.UUID
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class RealPlanCreationState(val inProgress: Boolean = false, val messageCode: String? = null)

/**
 * Only forwards commands and exposes state. It cannot fabricate catalog entries.
 */
class CatalogViewModel(
    private val repository: CatalogRepository,
    private val installedCatalog: HaloInstalledCatalogRepository,
    private val sessionRepository: SessionRepository,
    private val gateway: HaloLabGateway,
    private val clientProcessInstanceId: String,
) : ViewModel() {

    val state: StateFlow<CatalogState> = repository.state
    private val _realPlanCreation = MutableStateFlow(RealPlanCreationState())
    val realPlanCreation = _realPlanCreation.asStateFlow()

    fun setMode(mode: CatalogMode) {
        repository.setMode(mode)
    }

    fun setQuery(query: String) {
        repository.setQuery(query)
    }

    fun toggleScenario(scenarioId: String) {
        repository.toggleScenario(scenarioId)
    }

    fun clearSelection() {
        repository.clearSelection()
    }

    fun refreshRealInventory() {
        repository.setRealCatalogStatus(RealCatalogStatus.LOADING)
        viewModelScope.launch {
            when (val result = installedCatalog.load()) {
                is InstalledCatalogResult.Available -> repository.setRealCatalogStatus(RealCatalogStatus.READY, result.models)
                is InstalledCatalogResult.Empty -> repository.setRealCatalogStatus(RealCatalogStatus.EMPTY)
                is InstalledCatalogResult.Unavailable -> repository.setRealCatalogStatus(
                    result.reasonCode.toCatalogStatus(),
                    reason = result.reasonCode,
                )
            }
        }
    }

    fun createFastSmokePlan(model: InstalledModelDescriptor, onCreated: () -> Unit) {
        val current = repository.state.value
        if (_realPlanCreation.value.inProgress || model !in current.installedModels) return
        _realPlanCreation.value = RealPlanCreationState(inProgress = true)
        viewModelScope.launch {
            val result = try {
                val reply = gateway.handshake(
                    BridgeHandshakeRequest(
                        protocolVersion = BridgeProtocolVersion(1, 0),
                        requestId = UUID.randomUUID().toString(),
                        clientProcessInstanceId = clientProcessInstanceId,
                        clientSourceId = "not_recorded",
                        clientSchemaSha256 = com.slafftrosheen.halomodellab.AppContainer.LAB_CLIENT_SCHEMA_SHA256,
                        requestedFeatures = setOf(
                            BridgeFeature.MODEL_INVENTORY,
                            BridgeFeature.ARTIFACT_LEASES,
                            BridgeFeature.TEXT_EXECUTION,
                            BridgeFeature.EVENT_REPLAY,
                        ),
                    ),
                )
                if (reply.status != com.slafftrosheen.halomodellab.bridge.BridgeConnectionStatus.Ready ||
                    reply.protocolVersion?.negotiate(BridgeProtocolVersion(1, 0)) !is
                    com.slafftrosheen.halomodellab.bridge.ProtocolNegotiation.Compatible ||
                    reply.serverSchemaSha256 != com.slafftrosheen.halomodellab.AppContainer.HALO_PROTOCOL_SCHEMA_SHA256 ||
                    BridgeConsentScope.LOCAL_TEST_EXECUTION !in reply.grantedConsent
                ) {
                    "HALO_PROTOCOL_OR_LOCAL_TEST_CONSENT_REQUIRED"
                } else if (!setOf(BridgeFeature.ARTIFACT_LEASES, BridgeFeature.TEXT_EXECUTION, BridgeFeature.EVENT_REPLAY)
                        .all { it in reply.supportedFeatures }
                ) {
                    "HALO_REQUIRED_FEATURE_UNAVAILABLE"
                } else {
                    val capabilities = reply.runtimeCapabilities.firstOrNull { snapshot ->
                        snapshot.capabilities.any { capability ->
                            capability.role == ModelRole.FAST && RealBackend.CPU in capability.backends &&
                                capability.maximumOutputTokens?.let { it >= FastSmokePlanFactory.MAX_OUTPUT_TOKENS } == true
                        }
                    }
                    if (capabilities == null) {
                        "RUNTIME_FAST_CPU_CAPABILITY_MISSING"
                    } else {
                        val fingerprint = Build.FINGERPRINT.takeIf {
                            it.isNotBlank() && !it.equals("unknown", ignoreCase = true)
                        }
                        if (fingerprint == null) {
                            "DEVICE_BUILD_ID_UNAVAILABLE"
                        } else {
                            when (val plan = FastSmokePlanFactory.create(
                                model = model,
                                capabilities = capabilities,
                                deviceProfileId = UUID.nameUUIDFromBytes(fingerprint.toByteArray()).toString(),
                                osBuildId = fingerprint,
                            )) {
                                is FastSmokePlanResult.Unavailable -> plan.reasonCode
                                is FastSmokePlanResult.Ready -> when (sessionRepository.createRealSession(plan.plan)) {
                                    is CreateSessionResult.Created -> "REAL_PLAN_CREATED"
                                    is CreateSessionResult.Blocked -> "OPEN_SESSION_EXISTS"
                                }
                            }
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                "REAL_PLAN_CREATE_FAILED:${failure::class.java.simpleName}"
            }
            _realPlanCreation.value = RealPlanCreationState(messageCode = result)
            if (result == "REAL_PLAN_CREATED") onCreated()
        }
    }

    companion object {
        fun factory(
            repository: CatalogRepository,
            installedCatalog: HaloInstalledCatalogRepository,
            sessionRepository: SessionRepository,
            gateway: HaloLabGateway,
            clientProcessInstanceId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogViewModel(repository, installedCatalog, sessionRepository, gateway, clientProcessInstanceId) }
        }
    }
}

private fun String.toCatalogStatus(): RealCatalogStatus = when (this) {
    "HALO_NOT_INSTALLED" -> RealCatalogStatus.HALO_NOT_INSTALLED
    "BRIDGE_NOT_SUPPORTED" -> RealCatalogStatus.BRIDGE_NOT_SUPPORTED
    "SIGNATURE_MISMATCH" -> RealCatalogStatus.SIGNATURE_MISMATCH
    "CONSENT_REQUIRED" -> RealCatalogStatus.CONSENT_REQUIRED
    "PROTOCOL_MISMATCH" -> RealCatalogStatus.PROTOCOL_MISMATCH
    "BRIDGE_BUSY" -> RealCatalogStatus.BRIDGE_BUSY
    "BRIDGE_RECONNECTING" -> RealCatalogStatus.BRIDGE_RECONNECTING
    else -> RealCatalogStatus.UNAVAILABLE
}
