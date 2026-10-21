package com.slafftrosheen.halomodellab.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.HistoryFilter
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.SessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val sessions: List<SessionSummary> = emptyList(),
    val totalCount: Int = 0,
)

class HistoryViewModel(
    repository: SessionRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(
        repository.observeHistory(),
        filter,
    ) { sessions, selectedFilter ->
        HistoryUiState(
            filter = selectedFilter,
            sessions = sessions.filter { matches(it, selectedFilter) },
            totalCount = sessions.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    private fun matches(summary: SessionSummary, filter: HistoryFilter): Boolean = when (filter) {
        HistoryFilter.ALL -> true
        HistoryFilter.COMPLETED -> summary.state == SessionState.COMPLETED
        HistoryFilter.INTERRUPTED -> summary.state == SessionState.INTERRUPTED
        HistoryFilter.SYNTHETIC -> summary.origin == EvidenceOrigin.SYNTHETIC
    }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(repository) }
        }
    }
}
