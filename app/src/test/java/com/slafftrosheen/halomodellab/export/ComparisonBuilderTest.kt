package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class ComparisonBuilderTest {
    @Test
    fun exactVerifiedDeviceScopeProducesNewResolvedAndPersistentFailureSets() {
        val persistent = FailureSignature("MODEL_OUTPUT_MISMATCH", "MODEL", "EXECUTE")
        val oldOnly = FailureSignature("OLD_FAILURE", "RUNTIME", "LOAD")
        val newOnly = FailureSignature("NEW_FAILURE", "ADAPTER", "LOAD")
        val result = ComparisonBuilder.compare(
            run("current", setOf(persistent, newOnly)),
            run("previous", setOf(persistent, oldOnly)),
        )
        assertEquals(ComparisonStatus.COMPATIBLE, result.status)
        assertEquals(setOf(newOnly), result.newFailures)
        assertEquals(setOf(oldOnly), result.resolvedFailures)
        assertEquals(setOf(persistent), result.persistentFailures)
    }

    @Test
    fun syntheticUnknownOrChangedPolicyEvidenceIsIncomparable() {
        assertEquals(ComparisonStatus.INCOMPARABLE, ComparisonBuilder.compare(
            run("current", emptySet()).copy(origin = EvidenceOrigin.SYNTHETIC), run("previous", emptySet()),
        ).status)
        assertEquals("EXECUTION_SCOPE_CHANGED", ComparisonBuilder.compare(
            run("current", emptySet()), run("previous", emptySet()).copy(scopeDigestSha256 = "c".repeat(64)),
        ).reasonCode)
        assertEquals("QUALIFICATION_POLICY_CHANGED", ComparisonBuilder.compare(
            run("current", emptySet()), run("previous", emptySet()).copy(policyDigestSha256 = "d".repeat(64)),
        ).reasonCode)
    }

    @Test
    fun compatibleMetricDeltasRequireKnownValuesAndMatchingMetricIdentity() {
        val latency = MetricKey("generation_ms", "ms", "per_attempt")
        val memory = MetricKey("peak_memory", "MiB", "per_attempt")
        val overflow = MetricKey("large_counter", "units", "per_attempt")
        val current = run("current", emptySet()).copy(metrics = mapOf(
            latency to ComparableMetric(median = 120.0, unavailableReason = null),
            memory to ComparableMetric(median = null, unavailableReason = "THERMAL_ABORT"),
            overflow to ComparableMetric(median = Double.MAX_VALUE, unavailableReason = null),
        ))
        val previous = run("previous", emptySet()).copy(metrics = mapOf(
            latency to ComparableMetric(median = 100.0, unavailableReason = null),
            memory to ComparableMetric(median = 64.0, unavailableReason = null),
            overflow to ComparableMetric(median = -Double.MAX_VALUE, unavailableReason = null),
            MetricKey("generation_ms", "ms", "other_scope") to ComparableMetric(90.0, null),
        ))

        val deltas = ComparisonBuilder.compare(current, previous).metricDeltas.associateBy { it.key }
        assertEquals(20.0, deltas.getValue(latency).delta!!, 0.0)
        assertEquals(null, deltas.getValue(memory).delta)
        assertEquals("CURRENT:THERMAL_ABORT", deltas.getValue(memory).unavailableReason)
        assertEquals(null, deltas.getValue(overflow).delta)
        assertEquals("DELTA_NON_FINITE", deltas.getValue(overflow).unavailableReason)
        assertEquals(null, deltas.getValue(MetricKey("generation_ms", "ms", "other_scope")).delta)
        assertEquals("CURRENT_MEASUREMENT_UNKNOWN", deltas.getValue(
            MetricKey("generation_ms", "ms", "other_scope"),
        ).unavailableReason)
    }

    private fun run(id: String, failures: Set<FailureSignature>) = ComparableRun(
        sessionId = id, origin = EvidenceOrigin.DEVICE_MEASUREMENT,
        scopeDigestSha256 = "a".repeat(64), policyDigestSha256 = "b".repeat(64),
        sourceIdentitiesVerified = true, failures = failures,
    )
}
