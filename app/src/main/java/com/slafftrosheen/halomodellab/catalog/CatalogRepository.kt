package com.slafftrosheen.halomodellab.catalog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Catalog state for the Lab's simulation and read-only Halo inventory views.
 *
 * In-memory only: nothing here is persisted, and the selected scenarios are lost when the
 * process dies. Persisted plans arrive with Room in HML-L1.
 *
 * The real catalog is populated only from an explicit Halo inventory response. This class
 * must never invent entries, and it must never turn a synthetic scenario into real evidence.
 */
data class CatalogState(
    val mode: CatalogMode = CatalogMode.DEMO,
    val query: String = "",
    val selectedScenarioIds: List<String> = emptyList(),
    val realCatalogStatus: RealCatalogStatus = RealCatalogStatus.NOT_CHECKED,
    val realCatalogReason: String? = null,
    val installedModels: List<InstalledModelDescriptor> = emptyList(),
)

class CatalogRepository {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    fun setMode(mode: CatalogMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Selection order defines the future queue order; unknown ids are ignored. */
    fun toggleScenario(scenarioId: String) {
        if (DemoCatalog.scenarioById(scenarioId) == null) return
        _state.update { current ->
            val selected = current.selectedScenarioIds
            current.copy(
                selectedScenarioIds = if (scenarioId in selected) {
                    selected - scenarioId
                } else {
                    selected + scenarioId
                },
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedScenarioIds = emptyList()) }
    }

    fun setRealCatalogStatus(
        status: RealCatalogStatus,
        models: List<InstalledModelDescriptor> = emptyList(),
        reason: String? = null,
    ) {
        _state.update { it.copy(realCatalogStatus = status, realCatalogReason = reason, installedModels = models) }
    }
}
