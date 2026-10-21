package com.slafftrosheen.halomodellab.runner

import com.slafftrosheen.halomodellab.domain.StageBudgets

enum class RunPreset { QUICK, STANDARD, SOAK }

data class RealRunBudget(
    val preset: RunPreset,
    val sessionBudgetMs: Long,
    val stageBudgets: StageBudgets,
    val repetitions: Int,
    val plannedItems: Int,
    val requireCharging: Boolean,
    val diskReserveBytes: Long = DEFAULT_DISK_RESERVE_BYTES,
    val rawEvidenceBudgetBytes: Long = DEFAULT_RAW_EVIDENCE_BUDGET_BYTES,
) {
    init {
        require(sessionBudgetMs in MIN_SESSION_BUDGET_MS..MAX_SESSION_BUDGET_MS)
        require(repetitions in 1..MAX_REPETITIONS)
        require(plannedItems in 1..MAX_QUEUE_ITEMS)
        require(diskReserveBytes >= DEFAULT_DISK_RESERVE_BYTES)
        require(rawEvidenceBudgetBytes in 0L..MAX_RAW_EVIDENCE_BUDGET_BYTES)
        if (preset == RunPreset.SOAK) require(sessionBudgetMs >= SOAK_MINIMUM_MS)
    }

    fun effectiveAttemptDeadlineMs(remainingSessionBudgetMs: Long): Long {
        require(remainingSessionBudgetMs >= 0L)
        return minOf(stageBudgets.maximumAttemptMs, remainingSessionBudgetMs)
    }

    companion object {
        const val MIN_SESSION_BUDGET_MS = 60_000L
        const val MAX_SESSION_BUDGET_MS = 86_400_000L
        const val QUICK_DEFAULT_MS = 600_000L
        const val STANDARD_DEFAULT_MS = 1_800_000L
        const val SOAK_DEFAULT_MS = 7_200_000L
        const val SOAK_MINIMUM_MS = 3_600_000L
        const val MAX_REPETITIONS = 1_000
        const val MAX_QUEUE_ITEMS = 10_000
        const val DEFAULT_DISK_RESERVE_BYTES = 256L * 1024L * 1024L
        const val DEFAULT_RAW_EVIDENCE_BUDGET_BYTES = 64L * 1024L * 1024L
        const val MAX_RAW_EVIDENCE_BUDGET_BYTES = 512L * 1024L * 1024L

        fun defaults(preset: RunPreset, plannedItems: Int, repetitions: Int = 3): RealRunBudget =
            RealRunBudget(
                preset = preset,
                sessionBudgetMs = when (preset) {
                    RunPreset.QUICK -> QUICK_DEFAULT_MS
                    RunPreset.STANDARD -> STANDARD_DEFAULT_MS
                    RunPreset.SOAK -> SOAK_DEFAULT_MS
                },
                stageBudgets = StageBudgets(),
                repetitions = repetitions,
                plannedItems = plannedItems,
                requireCharging = true,
            )
    }
}

data class ActiveBudgetCheckpoint(
    val bootId: String,
    val lastElapsedRealtimeMs: Long,
    val chargedActiveMs: Long,
    val remainingAttemptBudgetMs: Long,
) {
    init {
        require(bootId.isNotBlank())
        require(lastElapsedRealtimeMs >= 0L && chargedActiveMs >= 0L && remainingAttemptBudgetMs >= 0L)
    }
}

object ActiveBudgetAccounting {
    /** Cross-boot monotonic samples are incomparable and never subtracted. */
    fun checkpoint(
        previous: ActiveBudgetCheckpoint,
        bootId: String,
        elapsedRealtimeMs: Long,
        observedActiveDeltaMs: Long?,
    ): ActiveBudgetCheckpoint {
        require(bootId.isNotBlank() && elapsedRealtimeMs >= 0L)
        val sameBoot = previous.bootId == bootId && elapsedRealtimeMs >= previous.lastElapsedRealtimeMs
        val knownDelta = if (sameBoot) observedActiveDeltaMs?.coerceAtLeast(0L) ?: 0L else 0L
        return previous.copy(
            bootId = bootId,
            lastElapsedRealtimeMs = elapsedRealtimeMs,
            chargedActiveMs = Math.addExact(previous.chargedActiveMs, knownDelta),
        )
    }

    /** An uncertain tail is charged conservatively, bounded by the attempt's saved remainder. */
    fun chargeUnknownTail(previous: ActiveBudgetCheckpoint, estimatedUnknownTailMs: Long): ActiveBudgetCheckpoint {
        require(estimatedUnknownTailMs >= 0L)
        val charge = minOf(estimatedUnknownTailMs, previous.remainingAttemptBudgetMs)
        return previous.copy(
            chargedActiveMs = Math.addExact(previous.chargedActiveMs, charge),
            remainingAttemptBudgetMs = previous.remainingAttemptBudgetMs - charge,
        )
    }
}

data class HarnessFaultCircuitBreaker(val consecutiveFaults: Int = 0) {
    init { require(consecutiveFaults >= 0) }

    fun recordHarnessFault(): HarnessFaultCircuitBreaker = copy(consecutiveFaults = consecutiveFaults + 1)
    fun recordValidControlSuccess(): HarnessFaultCircuitBreaker = copy(consecutiveFaults = 0)
    val shouldPauseForReview: Boolean get() = consecutiveFaults >= FAULTS_BEFORE_PAUSE

    companion object { const val FAULTS_BEFORE_PAUSE = 3 }
}
