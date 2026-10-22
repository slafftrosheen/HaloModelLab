package com.slafftrosheen.halomodellab.catalog

import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.ModelRole

/**
 * Which catalog the UI is allowed to show.
 *
 * Real inventory is read from Halo on explicit request; DEMO entries remain synthetic.
 */
enum class CatalogMode { REAL, DEMO }

/**
 * Status of a user-requested read-only inventory query. Do not infer absence from errors.
 */
enum class RealCatalogStatus {
    NOT_CHECKED, LOADING, READY, EMPTY, HALO_NOT_INSTALLED, BRIDGE_NOT_SUPPORTED,
    SIGNATURE_MISMATCH, CONSENT_REQUIRED, PROTOCOL_MISMATCH, BRIDGE_BUSY,
    BRIDGE_RECONNECTING, UNAVAILABLE,
}

/**
 * A controlled fixture, not a model.
 *
 * The id is a scenario id under `demo/` and must never collide with a real model id.
 * Display text lives in string resources; the UI resolves it from [id].
 */
data class DemoScenario(
    val id: String,
    val role: ModelRole,
    val requestedBackend: ComputeBackend,
)

object DemoCatalog {
    /** The only plan profile v0 offers. Real SMOKE/SOAK profiles belong to a later stage. */
    const val PROFILE_ID: String = "DEMO_INFRASTRUCTURE_V1"

    /** Engine id of the cooperative fake engine; it is not an inference runtime id. */
    const val DEMO_ENGINE_ID: String = "demo-engine-v1"

    const val SUCCESS: String = "demo/success"
    const val LOAD_FAILURE: String = "demo/load-failure"
    const val EMPTY_OUTPUT: String = "demo/empty-output"
    const val TIMEOUT: String = "demo/timeout"

    val scenarioIds: List<String> = listOf(SUCCESS, LOAD_FAILURE, EMPTY_OUTPUT, TIMEOUT)

    /**
     * CHAT/UNSPECIFIED are technical fixture categories recorded for future matching.
     * The UI must not present them as a CPU/GPU inference request.
     */
    val scenarios: List<DemoScenario> = scenarioIds.map { id ->
        DemoScenario(
            id = id,
            role = ModelRole.CHAT,
            requestedBackend = ComputeBackend.UNSPECIFIED,
        )
    }

    fun scenarioById(id: String): DemoScenario? = scenarios.firstOrNull { it.id == id }
}
