package com.slafftrosheen.halomodellab.domain

/**
 * Session lifecycle. Terminal states are never rewritten.
 *
 * DRAFT -> RUNNING -> COMPLETED
 * RUNNING -> PAUSING -> PAUSED -> RUNNING
 * RUNNING/PAUSING -> STOPPING -> CANCELLED
 * RUNNING/PAUSING/STOPPING -> INTERRUPTED (process loss)
 * DRAFT/PAUSED/INTERRUPTED -> CANCELLED (user refuses to continue)
 * INTERRUPTED -> RUNNING (only after an explicit user decision)
 */
enum class SessionState {
    DRAFT,
    RUNNING,
    PAUSING,
    PAUSED,
    STOPPING,
    COMPLETED,
    CANCELLED,
    INTERRUPTED,
    FAILED,
}

/** Queue item states. NEEDS_DECISION means the interrupted item was not automatically resumed. */
enum class QueueItemState { PENDING, ACTIVE, DONE, SKIPPED, NEEDS_DECISION }

/** Why the effective attempt deadline was shorter than the case timeout. */
enum class DeadlineReason { CASE_TIMEOUT, SESSION_BUDGET }

enum class SessionCommand {
    START,
    PAUSE_REQUESTED,
    PAUSE_APPLIED,
    STOP_REQUESTED,
    STOP_APPLIED,
    QUEUE_EXHAUSTED,
    PROCESS_LOST,
    CANCEL,
    INFRASTRUCTURE_FAILURE,
}

enum class HistoryFilter { ALL, COMPLETED, INTERRUPTED, SYNTHETIC }

fun SessionState.isTerminal(): Boolean =
    this == SessionState.COMPLETED || this == SessionState.CANCELLED || this == SessionState.FAILED

fun SessionState.isOpen(): Boolean = !isTerminal()

fun SessionState.allowsResume(): Boolean =
    this == SessionState.PAUSED || this == SessionState.INTERRUPTED

/** Attempt rows are active while [outcome] is null; a terminal result is written once. */
fun attemptIsTerminal(outcome: AttemptOutcome?): Boolean = outcome != null

object SessionStates {
    val TERMINAL_NAMES: List<String> = listOf(
        SessionState.COMPLETED.name,
        SessionState.CANCELLED.name,
        SessionState.FAILED.name,
    )

    val OPEN_NAMES: List<String> = listOf(
        SessionState.DRAFT.name,
        SessionState.RUNNING.name,
        SessionState.PAUSING.name,
        SessionState.PAUSED.name,
        SessionState.STOPPING.name,
        SessionState.INTERRUPTED.name,
    )

    /** States whose owner may still be executing work. */
    val LIVE_NAMES: List<String> = listOf(
        SessionState.RUNNING.name,
        SessionState.PAUSING.name,
        SessionState.STOPPING.name,
    )

    val RESOLVED_ITEM_NAMES: List<String> = listOf(
        QueueItemState.DONE.name,
        QueueItemState.SKIPPED.name,
    )
}

/**
 * Pure transition table. Returning `null` means the command is not allowed from that state.
 *
 * Rotation, tab changes and Activity recreation are not commands: only an explicit user action
 * is, which is why there is no "RECREATE" transition here.
 */
object SessionStateMachine {

    fun next(
        current: SessionState,
        command: SessionCommand,
        allItemsResolved: Boolean = false,
    ): SessionState? {
        if (current.isTerminal()) return null

        return when (command) {
            SessionCommand.START -> when (current) {
                SessionState.DRAFT,
                SessionState.PAUSED,
                SessionState.INTERRUPTED,
                -> SessionState.RUNNING

                else -> null
            }

            SessionCommand.PAUSE_REQUESTED -> when (current) {
                SessionState.RUNNING -> SessionState.PAUSING
                else -> null
            }

            SessionCommand.PAUSE_APPLIED -> when (current) {
                SessionState.PAUSING -> if (allItemsResolved) {
                    SessionState.COMPLETED
                } else {
                    SessionState.PAUSED
                }

                else -> null
            }

            SessionCommand.STOP_REQUESTED -> when (current) {
                SessionState.RUNNING, SessionState.PAUSING -> SessionState.STOPPING
                else -> null
            }

            SessionCommand.STOP_APPLIED -> when (current) {
                SessionState.STOPPING -> SessionState.CANCELLED
                else -> null
            }

            SessionCommand.QUEUE_EXHAUSTED -> when (current) {
                SessionState.RUNNING, SessionState.PAUSING -> SessionState.COMPLETED
                else -> null
            }

            // A paused session stays paused: nothing was executing, so it is not interrupted.
            SessionCommand.PROCESS_LOST -> when (current) {
                SessionState.RUNNING,
                SessionState.PAUSING,
                SessionState.STOPPING,
                -> SessionState.INTERRUPTED

                SessionState.PAUSED -> SessionState.PAUSED
                SessionState.DRAFT -> SessionState.DRAFT
                else -> null
            }

            SessionCommand.CANCEL -> when (current) {
                SessionState.DRAFT,
                SessionState.PAUSED,
                SessionState.INTERRUPTED,
                -> SessionState.CANCELLED

                else -> null
            }

            SessionCommand.INFRASTRUCTURE_FAILURE -> SessionState.FAILED
        }
    }

    fun canApply(current: SessionState, command: SessionCommand, allItemsResolved: Boolean = false): Boolean =
        next(current, command, allItemsResolved) != null

    /** Queue item state after a terminal attempt outcome. */
    fun queueItemStateFor(outcome: AttemptOutcome): QueueItemState = when (outcome) {
        AttemptOutcome.PASSED,
        AttemptOutcome.FAILED,
        AttemptOutcome.TIMED_OUT,
        AttemptOutcome.CANCELLED,
        -> QueueItemState.DONE

        AttemptOutcome.INTERRUPTED -> QueueItemState.NEEDS_DECISION
        AttemptOutcome.SKIPPED -> QueueItemState.SKIPPED
    }
}
