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

    private fun run(id: String, failures: Set<FailureSignature>) = ComparableRun(
        sessionId = id, origin = EvidenceOrigin.DEVICE_MEASUREMENT,
        scopeDigestSha256 = "a".repeat(64), policyDigestSha256 = "b".repeat(64),
        sourceIdentitiesVerified = true, failures = failures,
    )
}
