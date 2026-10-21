package com.slafftrosheen.halomodellab.runner

import com.slafftrosheen.halomodellab.domain.StageBudgets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunBudgetPolicyTest {
    @Test
    fun presetDefaultsAndAttemptDeadlineAreBoundedBySessionRemainder() {
        val quick = RealRunBudget.defaults(RunPreset.QUICK, plannedItems = 4)
        assertEquals(RealRunBudget.QUICK_DEFAULT_MS, quick.sessionBudgetMs)
        assertEquals(10_000L, quick.effectiveAttemptDeadlineMs(10_000L))
        assertEquals(0L, quick.effectiveAttemptDeadlineMs(0L))
    }

    @Test
    fun soakRequiresAnExplicitLongRunBudget() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            RealRunBudget(RunPreset.SOAK, 30 * 60_000L, StageBudgets(), 1, 1, true)
        }
    }

    @Test
    fun unknownTailIsConservativelyChargedAndBounded() {
        val initial = ActiveBudgetCheckpoint("boot-a", 1_000L, 2_000L, 5_000L)
        val charged = ActiveBudgetAccounting.chargeUnknownTail(initial, 8_000L)
        assertEquals(7_000L, charged.chargedActiveMs)
        assertEquals(0L, charged.remainingAttemptBudgetMs)
    }

    @Test
    fun elapsedValuesAcrossRebootAreNeverSubtracted() {
        val initial = ActiveBudgetCheckpoint("boot-a", 99_000L, 2_000L, 10_000L)
        val recovered = ActiveBudgetAccounting.checkpoint(initial, "boot-b", 50L, 50_000L)
        assertEquals(2_000L, recovered.chargedActiveMs)
    }

    @Test
    fun repeatedHarnessFaultsTripCircuitBreakerUntilControlSuccess() {
        val breaker = HarnessFaultCircuitBreaker().recordHarnessFault().recordHarnessFault().recordHarnessFault()
        assertTrue(breaker.shouldPauseForReview)
        assertEquals(0, breaker.recordValidControlSuccess().consecutiveFaults)
    }
}
