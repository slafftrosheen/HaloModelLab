package com.slafftrosheen.halomodellab.qualification

import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.ResultValidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualificationPolicyTest {
    @Test
    fun oneModelAssertionFailureNeverExcludesExactScope() {
        val decision = QualificationEvaluator.evaluate(SCOPE, POLICY, listOf(observation(
            attemptId = "attempt-1", worker = "worker-1", assertion = CheckAssertion.FAIL,
            domain = FailureDomain.MODEL,
        )), scopeIdentityVerifiedForQualification = true)

        assertEquals(QualificationStatus.INCONCLUSIVE, decision.status)
        assertFalse(decision.mayHideExactScope)
    }

    @Test
    fun repeatedSameAssertionAcrossIndependentWorkersExcludesOnlyThisScope() {
        val decision = QualificationEvaluator.evaluate(
            SCOPE,
            POLICY,
            listOf(
                observation("attempt-1", "worker-1", CheckAssertion.FAIL, FailureDomain.MODEL),
                observation("attempt-2", "worker-2", CheckAssertion.FAIL, FailureDomain.MODEL),
            ),
            scopeIdentityVerifiedForQualification = true,
        )

        assertEquals(QualificationStatus.REJECTED_FOR_PROFILE, decision.status)
        assertTrue(decision.mayHideExactScope)
        assertEquals(listOf("attempt-1", "attempt-2"), decision.eligibleAttemptIds)
    }

    @Test
    fun syntheticAndUnknownScopeEvidenceCannotQualifyOrReject() {
        val synthetic = observation("attempt-1", "worker-1", CheckAssertion.PASS, FailureDomain.MODEL)
            .copy(origin = EvidenceOrigin.SYNTHETIC)
        assertEquals(
            QualificationStatus.INCONCLUSIVE,
            QualificationEvaluator.evaluate(SCOPE, POLICY, listOf(synthetic), scopeIdentityVerifiedForQualification = true).status,
        )
        val unknownScope = QualificationEvaluator.evaluate(null, POLICY, listOf(synthetic))
        assertEquals(QualificationStatus.INCONCLUSIVE, unknownScope.status)
        assertFalse(unknownScope.mayHideExactScope)
    }

    @Test
    fun scopeOrPolicyChangeMarksOldEvidenceStale() {
        val old = observation("attempt-1", "worker-1", CheckAssertion.PASS, FailureDomain.UNKNOWN)
            .copy(scopeDigestSha256 = OTHER_SCOPE)
        assertEquals(QualificationStatus.STALE, QualificationEvaluator.evaluate(SCOPE, POLICY, listOf(old), scopeIdentityVerifiedForQualification = true).status)
        val otherPolicy = POLICY.copy(policyDigestSha256 = OTHER_POLICY)
        assertEquals(QualificationStatus.STALE, QualificationEvaluator.evaluate(SCOPE, otherPolicy, listOf(old.copy(scopeDigestSha256 = SCOPE)), scopeIdentityVerifiedForQualification = true).status)
    }

    @Test
    fun performanceRejectionNeedsThreeTrustedComparableSamples() {
        val policy = POLICY.copy(performanceThresholds = listOf(PerformanceThreshold("latency", "ms", 100.0)))
        val one = observation("attempt-1", "worker-1", CheckAssertion.PASS, FailureDomain.UNKNOWN)
            .copy(metricName = "latency", metricUnit = "ms", metricValue = 150.0, metricTrusted = true)
        val two = one.copy(attemptId = "attempt-2", workerInstanceId = "worker-2")
        assertFalse(QualificationEvaluator.evaluate(SCOPE, policy, listOf(one, two), scopeIdentityVerifiedForQualification = true).mayHideExactScope)
        val three = one.copy(attemptId = "attempt-3", workerInstanceId = "worker-3")
        assertEquals(
            QualificationStatus.REJECTED_FOR_PROFILE,
            QualificationEvaluator.evaluate(SCOPE, policy, listOf(one, two, three), scopeIdentityVerifiedForQualification = true).status,
        )
    }

    @Test
    fun conflictingValidPassAndFailureIsInconclusive() {
        val pass = observation("attempt-1", "worker-1", CheckAssertion.PASS, FailureDomain.UNKNOWN)
        val fail = observation("attempt-2", "worker-2", CheckAssertion.FAIL, FailureDomain.MODEL)
        val decision = QualificationEvaluator.evaluate(
            SCOPE, POLICY, listOf(pass, fail), scopeIdentityVerifiedForQualification = true,
        )
        assertEquals(QualificationStatus.INCONCLUSIVE, decision.status)
        assertFalse(decision.mayHideExactScope)
    }

    @Test
    fun unknownEffectiveIdentityCannotQualifyEvenWithAWellFormedHash() {
        val pass = observation("attempt-1", "worker-1", CheckAssertion.PASS, FailureDomain.UNKNOWN)
        assertEquals(
            QualificationStatus.INCONCLUSIVE,
            QualificationEvaluator.evaluate(SCOPE, POLICY, listOf(pass)).status,
        )
    }

    private fun observation(
        attemptId: String,
        worker: String,
        assertion: CheckAssertion,
        domain: FailureDomain,
    ) = QualificationObservation(
        attemptId = attemptId,
        scopeDigestSha256 = SCOPE,
        policyDigestSha256 = POLICY.policyDigestSha256,
        checkId = "smoke",
        origin = EvidenceOrigin.DEVICE_MEASUREMENT,
        validity = ResultValidity.VALID,
        outcome = if (assertion == CheckAssertion.PASS) AttemptOutcome.PASSED else AttemptOutcome.FAILED,
        workerInstanceId = worker,
        fixtureManifestKnown = true,
        harnessControlsKnown = true,
        assertion = assertion,
        failureDomain = domain,
    )

    private companion object {
        const val SCOPE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OTHER_SCOPE = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val OTHER_POLICY = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        val POLICY = QualificationPolicy(
            policyDigestSha256 = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
            requiredCheckIds = setOf("smoke"),
        )
    }
}
