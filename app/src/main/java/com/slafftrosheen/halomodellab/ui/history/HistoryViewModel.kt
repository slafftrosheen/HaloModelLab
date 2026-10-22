package com.slafftrosheen.halomodellab.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.data.QualificationEntity
import com.slafftrosheen.halomodellab.qualification.QualificationRepository
import com.slafftrosheen.halomodellab.qualification.QualificationStatus
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
    val showExcluded: Boolean = false,
    val excludedScopes: List<QualificationEntity> = emptyList(),
)

class HistoryViewModel(
    repository: SessionRepository,
    qualificationRepository: QualificationRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val showExcluded = MutableStateFlow(false)

    val state: StateFlow<HistoryUiState> = combine(
        repository.observeHistory(),
        filter,
        qualificationRepository.observeHistory(),
        showExcluded,
    ) { sessions, selectedFilter, qualifications, showExcluded ->
        HistoryUiState(
            filter = selectedFilter,
            sessions = sessions.filter { matches(it, selectedFilter) },
            totalCount = sessions.size,
            showExcluded = showExcluded,
            excludedScopes = if (showExcluded) qualifications.filter {
                it.status == QualificationStatus.REJECTED_FOR_PROFILE.name
            } else emptyList(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    fun setShowExcluded(value: Boolean) { showExcluded.value = value }

    private fun matches(summary: SessionSummary, filter: HistoryFilter): Boolean = when (filter) {
        HistoryFilter.ALL -> true
        HistoryFilter.COMPLETED -> summary.state == SessionState.COMPLETED
        HistoryFilter.INTERRUPTED -> summary.state == SessionState.INTERRUPTED
        HistoryFilter.SYNTHETIC -> summary.origin == EvidenceOrigin.SYNTHETIC
    }

    companion object {
        fun factory(repository: SessionRepository, qualificationRepository: QualificationRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(repository, qualificationRepository) }
        }
    }
}
