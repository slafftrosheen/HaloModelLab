package com.slafftrosheen.halomodellab.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.slafftrosheen.halomodellab.StartupState
import com.slafftrosheen.halomodellab.catalog.CatalogRepository
import com.slafftrosheen.halomodellab.catalog.DemoCatalog
import com.slafftrosheen.halomodellab.catalog.DemoScenario
import com.slafftrosheen.halomodellab.data.CreateSessionResult
import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.PlanLimits
import com.slafftrosheen.halomodellab.domain.PlannedItem
import com.slafftrosheen.halomodellab.domain.QueueItemState
import com.slafftrosheen.halomodellab.domain.QueueItemView
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionPlan
import com.slafftrosheen.halomodellab.domain.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionMessage {
    data object Created : SessionMessage
    data class Blocked(val state: SessionState) : SessionMessage
    data object NoScenarios : SessionMessage
    data object InvalidPlan : SessionMessage
    data object RetryPlanned : SessionMessage
    data object SkipPlanned : SessionMessage
    data class StorageFailure(val detail: String) : SessionMessage
}

data class SessionUiState(
    val startup: StartupState,
    val openSession: SessionDetail?,
    val selectedScenarios: List<DemoScenario>,
    val repeats: Int,
    val timeoutSeconds: Int,
    val budgetMinutes: Int,
    val requireCharging: Boolean,
    val creating: Boolean,
    val message: SessionMessage?,
) {
    val plannedItemCount: Int
        get() = selectedScenarios.size * repeats

    val canCreate: Boolean
        get() = startup is StartupState.Ready &&
            openSession == null &&
            selectedScenarios.isNotEmpty() &&
            !creating

    val needsDecisionItems: List<QueueItemView>
        get() = openSession?.queue.orEmpty().filter { it.state == QueueItemState.NEEDS_DECISION }

    val sessionState: SessionState?
        get() = openSession?.summary?.state

    val canStartOrResume: Boolean
        get() = startup is StartupState.Ready &&
            (
                sessionState == SessionState.DRAFT ||
                    sessionState == SessionState.PAUSED ||
                    sessionState == SessionState.INTERRUPTED
                ) &&
            needsDecisionItems.isEmpty()

    val canPause: Boolean
        get() = sessionState == SessionState.RUNNING

    val canStop: Boolean
        get() = sessionState == SessionState.RUNNING || sessionState == SessionState.PAUSING

    val canCancel: Boolean
        get() = sessionState == SessionState.DRAFT ||
            sessionState == SessionState.PAUSED ||
            sessionState == SessionState.INTERRUPTED
}

private data class LocalState(
    val repeats: Int = PlanLimits.DEFAULT_REPEATS,
    val timeoutSeconds: Int = (PlanLimits.DEFAULT_CASE_TIMEOUT_MS / 1_000L).toInt(),
    val budgetMinutes: Int = (PlanLimits.DEFAULT_BUDGET_MS / 60_000L).toInt(),
    val requireCharging: Boolean = true,
    val creating: Boolean = false,
    val message: SessionMessage? = null,
)

/**
 * Builds and stores an immutable plan and forwards explicit user decisions.
 *
 * It never runs the queue: execution belongs to the foreground service and coordinator.
 * The ViewModel only writes durable state and observes Room.
 */
class SessionViewModel(
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
    startupState: StateFlow<StartupState>,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<SessionUiState> = combine(
        catalogRepository.state,
        sessionRepository.observeOpenSession(),
        startupState,
        local,
    ) { catalog, openSession, startup, localState ->
        SessionUiState(
            startup = startup,
            openSession = openSession,
            selectedScenarios = catalog.selectedScenarioIds.mapNotNull(DemoCatalog::scenarioById),
            repeats = localState.repeats,
            timeoutSeconds = localState.timeoutSeconds,
            budgetMinutes = localState.budgetMinutes,
            requireCharging = localState.requireCharging,
            creating = localState.creating,
            message = localState.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionUiState(
            startup = StartupState.Recovering,
            openSession = null,
            selectedScenarios = emptyList(),
            repeats = PlanLimits.DEFAULT_REPEATS,
            timeoutSeconds = (PlanLimits.DEFAULT_CASE_TIMEOUT_MS / 1_000L).toInt(),
            budgetMinutes = (PlanLimits.DEFAULT_BUDGET_MS / 60_000L).toInt(),
            requireCharging = true,
            creating = false,
            message = null,
        ),
    )

    fun setRepeats(value: Int) {
        local.update { it.copy(repeats = value.coerceIn(PlanLimits.REPEATS)) }
    }

    fun setTimeoutSeconds(value: Int) {
        local.update { it.copy(timeoutSeconds = value.coerceIn(1, 120)) }
    }

    fun setBudgetMinutes(value: Int) {
        local.update { it.copy(budgetMinutes = value.coerceIn(1, 60)) }
    }

    fun setRequireCharging(enabled: Boolean) {
        local.update { it.copy(requireCharging = enabled) }
    }

    fun clearSelection() {
        catalogRepository.clearSelection()
    }

    fun dismissMessage() {
        local.update { it.copy(message = null) }
    }

    fun createSession() {
        val current = state.value
        if (!current.canCreate) return

        val plan = buildPlan(current)
        if (plan == null) {
            local.update {
                it.copy(
                    message = if (current.selectedScenarios.isEmpty()) {
                        SessionMessage.NoScenarios
                    } else {
                        SessionMessage.InvalidPlan
                    },
                )
            }
            return
        }

        local.update { it.copy(creating = true, message = null) }
        viewModelScope.launch {
            val message = try {
                when (val result = sessionRepository.createSession(plan)) {
                    is CreateSessionResult.Created -> SessionMessage.Created
                    is CreateSessionResult.Blocked -> SessionMessage.Blocked(result.existingState)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                SessionMessage.StorageFailure(failure.message ?: failure::class.java.simpleName)
            }
            local.update { it.copy(creating = false, message = message) }
        }
    }

    fun cancelOpenSession() {
        val detail = state.value.openSession ?: return
        if (!state.value.canCancel) return
        launchStorageCommand {
            sessionRepository.cancelSession(
                sessionId = detail.summary.sessionId,
                reasonCode = "USER_DECLINED_CONTINUATION",
            )
        }
    }

    fun retryItem(queueItemId: String) {
        launchStorageCommand(
            block = { sessionRepository.retryItem(queueItemId) },
            successMessage = SessionMessage.RetryPlanned,
        )
    }

    fun skipItem(queueItemId: String) {
        launchStorageCommand(
            block = { sessionRepository.skipItem(queueItemId) },
            successMessage = SessionMessage.SkipPlanned,
        )
    }

    private fun launchStorageCommand(
        block: suspend () -> Any?,
        successMessage: SessionMessage? = null,
    ) {
        viewModelScope.launch {
            try {
                block()
                if (successMessage != null) local.update { it.copy(message = successMessage) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                local.update {
                    it.copy(
                        message = SessionMessage.StorageFailure(
                            failure.message ?: failure::class.java.simpleName,
                        ),
                    )
                }
            }
        }
    }

    private fun buildPlan(uiState: SessionUiState): SessionPlan? {
        if (uiState.selectedScenarios.isEmpty()) return null

        val items = buildList {
            for (repeatIndex in 0 until uiState.repeats) {
                uiState.selectedScenarios.forEach { scenario ->
                    add(
                        PlannedItem(
                            subjectId = scenario.id,
                            scenarioId = scenario.id,
                            role = scenario.role,
                            requestedBackend = scenario.requestedBackend,
                            repeatIndex = repeatIndex,
                        ),
                    )
                }
            }
        }

        return try {
            SessionPlan(
                profileId = DemoCatalog.PROFILE_ID,
                origin = EvidenceOrigin.SYNTHETIC,
                engineId = DemoCatalog.DEMO_ENGINE_ID,
                caseTimeoutMs = uiState.timeoutSeconds.toLong() * 1_000L,
                budgetMs = uiState.budgetMinutes.toLong() * 60_000L,
                repeatCount = uiState.repeats,
                requireCharging = uiState.requireCharging,
                items = items,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        fun factory(
            catalogRepository: CatalogRepository,
            sessionRepository: SessionRepository,
            startupState: StateFlow<StartupState>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SessionViewModel(
                    catalogRepository = catalogRepository,
                    sessionRepository = sessionRepository,
                    startupState = startupState,
                )
            }
        }
    }
}
