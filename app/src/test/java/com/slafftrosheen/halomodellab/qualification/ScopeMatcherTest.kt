package com.slafftrosheen.halomodellab.qualification

import com.slafftrosheen.halomodellab.domain.ExecutionScope
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.RealBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeMatcherTest {
    @Test
    fun exactKnownScopeCanReuseButAnyIdentityChangeRequiresRetest() {
        val current = scope()
        assertTrue(ScopeMatcher.matchesForReuse(current.canonicalSha256(), current.qualificationPolicySha256, current))
        assertFalse(ScopeMatcher.matchesForReuse("a".repeat(64), current.qualificationPolicySha256, current))
        assertFalse(ScopeMatcher.matchesForReuse(current.canonicalSha256(), "b".repeat(64), current))

        val plan = RetestPlanner.plan(current, "a".repeat(64), current.qualificationPolicySha256,
            QualificationStatus.QUALIFIED, setOf("short-ready"))
        assertEquals(RetestDisposition.RETEST_FULL_PROFILE, plan.disposition)
        assertEquals("SCOPE_OR_POLICY_CHANGED", plan.reasonCode)
    }

    @Test
    fun unknownBackendOrSourceCanBeRetestedButCannotReuseEvidence() {
        val unknown = scope().copy(effectiveBackend = null, sourceIdentitiesVerified = false)
        val plan = RetestPlanner.plan(unknown, unknown.canonicalSha256(), unknown.qualificationPolicySha256,
            QualificationStatus.QUALIFIED, setOf("short-ready"))
        assertEquals(RetestDisposition.RETEST_FULL_PROFILE, plan.disposition)
        assertEquals("CURRENT_SCOPE_UNVERIFIED_FRESH_RUN_WILL_NOT_QUALIFY", plan.reasonCode)
        assertEquals(setOf("short-ready"), plan.checkIds)
        assertFalse(ScopeMatcher.matchesForReuse(unknown.canonicalSha256(), unknown.qualificationPolicySha256, unknown))
    }

    @Test
    fun repeatFailedChecksIsExplicitAndBoundToRequiredChecks() {
        val current = scope()
        val plan = RetestPlanner.plan(current, current.canonicalSha256(), current.qualificationPolicySha256,
            QualificationStatus.PARTIAL, setOf("smoke", "warm"), setOf("smoke", "unknown"), repeatFailedChecks = true)
        assertEquals(RetestDisposition.RETEST_FAILED_CHECKS, plan.disposition)
        assertEquals(setOf("smoke"), plan.checkIds)
    }

    private fun scope() = ExecutionScope(
        deviceProfileId = "11111111-2222-4333-8444-555555555555",
        osBuildId = "device-build-1", haloSourceId = "halo-source-1", labSourceId = "lab-source-1",
        runtimeId = "litert", runtimeVersion = "1.0", adapterCompatibilityId = "adapter-v1",
        artifactManifestSha256 = "a".repeat(64), role = ModelRole.FAST,
        requestedBackend = RealBackend.CPU, effectiveBackend = RealBackend.CPU,
        effectiveConfigurationSha256 = "b".repeat(64), suiteId = "fast-smoke", suiteVersion = "1",
        fixtureManifestSha256 = "c".repeat(64), qualificationPolicyVersion = 1,
        qualificationPolicySha256 = "d".repeat(64), measurementMode = "COLD_PROCESS",
        language = "en", voiceId = null, sourceIdentitiesVerified = true,
    )
}
