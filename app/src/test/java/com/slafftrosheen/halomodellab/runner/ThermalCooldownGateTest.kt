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
}
