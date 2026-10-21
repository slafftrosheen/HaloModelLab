package com.slafftrosheen.halomodellab.catalog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Catalog state for HML-L0.
 *
 * In-memory only: nothing here is persisted, and the selected scenarios are lost when the
 * process dies. Persisted plans arrive with Room in HML-L1.
 *
 * The real catalog is always empty because the Halo bridge is not implemented. This class
 * must never invent entries, and it must never turn a synthetic scenario into real evidence.
 */
data class CatalogState(
    val mode: CatalogMode = CatalogMode.REAL_UNAVAILABLE,
    val query: String = "",
    val selectedScenarioIds: List<String> = emptyList(),
    val realCatalogStatus: RealCatalogStatus = RealCatalogStatus.BRIDGE_NOT_IMPLEMENTED,
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
}
