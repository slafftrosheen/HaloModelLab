package com.slafftrosheen.halomodellab.runner

import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalCooldownGateTest {
    @Test fun unknownAndModerateReadingsBlockAndDoNotClearCooldown() {
        val gate = ThermalCooldownGate()
        assertEquals(ThermalGateDecision.Blocked(ConditionBlockReason.THERMAL_CONSTRAINT),
            gate.sample(ThermalLevel.SEVERE, 100L))
        assertEquals(ThermalGateDecision.Blocked(ConditionBlockReason.THERMAL_STATUS_UNKNOWN),
            gate.sample(ThermalLevel.UNKNOWN, 200L))
        assertEquals(ThermalGateDecision.Blocked(ConditionBlockReason.THERMAL_CONSTRAINT),
            gate.sample(ThermalLevel.MODERATE, 300L))
        assertEquals(ThermalGateDecision.CoolingDown(60_000L), gate.sample(ThermalLevel.LIGHT, 400L))
    }

    @Test fun requiresFullMinuteOfContinuousSafeReadingsAfterSevereThermalStatus() {
        val gate = ThermalCooldownGate()
        gate.sample(ThermalLevel.CRITICAL, 1_000L)
        assertEquals(ThermalGateDecision.CoolingDown(40_000L), gate.sample(ThermalLevel.NONE, 21_000L))
        assertEquals(ThermalGateDecision.Blocked(ConditionBlockReason.THERMAL_STATUS_UNKNOWN),
            gate.sample(ThermalLevel.UNKNOWN, 31_000L))
        assertEquals(ThermalGateDecision.CoolingDown(60_000L), gate.sample(ThermalLevel.LIGHT, 41_000L))
        assertEquals(ThermalGateDecision.Ready, gate.sample(ThermalLevel.NONE, 101_000L))
    }

    @Test fun cooldownCheckpointSurvivesGateRecreationAndWritesOnlyOnTransitions() {
        val store = CountingStore()
        val first = ThermalCooldownGate(store = store)
        first.sample(ThermalLevel.SEVERE, 10_000L)
        first.sample(ThermalLevel.SEVERE, 11_000L)
        assertEquals(1, store.writeCount)

        val resumed = ThermalCooldownGate(store = store)
        assertEquals(ThermalGateDecision.CoolingDown(50_000L), resumed.sample(ThermalLevel.LIGHT, 20_000L))
        assertEquals(ThermalGateDecision.Ready, resumed.sample(ThermalLevel.NONE, 80_000L))
        assertEquals(3, store.writeCount)
    }

    @Test fun checkpointWriteFailureKeepsCooldownClosed() {
        val store = FailingStore()
        val gate = ThermalCooldownGate(store = store)

        assertEquals(ThermalGateDecision.Blocked(ConditionBlockReason.THERMAL_CONSTRAINT),
            gate.sample(ThermalLevel.SEVERE, 1_000L))
        assertEquals(ThermalGateDecision.CoolingDown(60_000L), gate.sample(ThermalLevel.LIGHT, 2_000L))
        assertEquals(ThermalGateDecision.CoolingDown(60_000L), gate.sample(ThermalLevel.LIGHT, 62_000L))
    }

    private class CountingStore : ThermalCooldownStore {
        var checkpoint = ThermalCooldownCheckpoint(false, null)
        var writeCount = 0
        override fun read(): ThermalCooldownCheckpoint = checkpoint
        override fun write(checkpoint: ThermalCooldownCheckpoint) {
            this.checkpoint = checkpoint
            writeCount++
        }
    }

    private class FailingStore : ThermalCooldownStore {
        override fun read(): ThermalCooldownCheckpoint = ThermalCooldownCheckpoint(false, null)
        override fun write(checkpoint: ThermalCooldownCheckpoint) { error("PREFERENCE_WRITE_FAILED") }
    }
}
