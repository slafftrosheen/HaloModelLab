package com.slafftrosheen.halomodellab.ui

import androidx.annotation.StringRes
import com.slafftrosheen.halomodellab.R
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.QueueItemState
import com.slafftrosheen.halomodellab.domain.SessionState

@StringRes
fun sessionStateLabelRes(state: SessionState): Int = when (state) {
    SessionState.DRAFT -> R.string.state_draft
    SessionState.RUNNING -> R.string.state_running
    SessionState.PAUSING -> R.string.state_pausing
    SessionState.PAUSED -> R.string.state_paused
    SessionState.STOPPING -> R.string.state_stopping
    SessionState.COMPLETED -> R.string.state_completed
    SessionState.CANCELLED -> R.string.state_cancelled
    SessionState.INTERRUPTED -> R.string.state_interrupted
    SessionState.FAILED -> R.string.state_failed
}

@StringRes
fun queueItemStateLabelRes(state: QueueItemState): Int = when (state) {
    QueueItemState.PENDING -> R.string.queue_pending
    QueueItemState.ACTIVE -> R.string.queue_active
    QueueItemState.DONE -> R.string.queue_done
    QueueItemState.SKIPPED -> R.string.queue_skipped
    QueueItemState.NEEDS_DECISION -> R.string.queue_needs_decision
}

@StringRes
fun attemptOutcomeLabelRes(outcome: AttemptOutcome?): Int = when (outcome) {
    null -> R.string.outcome_pending
    AttemptOutcome.PASSED -> R.string.outcome_passed
    AttemptOutcome.FAILED -> R.string.outcome_failed
    AttemptOutcome.TIMED_OUT -> R.string.outcome_timed_out
    AttemptOutcome.CANCELLED -> R.string.outcome_cancelled
    AttemptOutcome.INTERRUPTED -> R.string.outcome_interrupted
    AttemptOutcome.SKIPPED -> R.string.outcome_skipped
}

@StringRes
fun originLabelRes(origin: EvidenceOrigin): Int = when (origin) {
    EvidenceOrigin.SYNTHETIC -> R.string.origin_synthetic
    EvidenceOrigin.DEVICE_MEASUREMENT -> R.string.origin_device
    EvidenceOrigin.IMPORTED_UNVERIFIED -> R.string.origin_imported
}
