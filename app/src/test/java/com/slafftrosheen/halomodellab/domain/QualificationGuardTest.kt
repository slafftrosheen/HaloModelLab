package com.slafftrosheen.halomodellab.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level checks for [isEligibleForDeviceQualification].
 *
 * These tests are added in HML-L0 but were NOT executed: see docs/IMPLEMENTATION_STATUS.md.
 */
class QualificationGuardTest {

    private fun result(
        outcome: AttemptOutcome,
        origin: EvidenceOrigin,
        validity: ResultValidity = ResultValidity.VALID,
        failure: FailureInfo? = null,
    ): AttemptResult = AttemptResult(
        outcome = outcome,
        validity = validity,
        origin = origin,
        failure = failure,
    )

    private fun failure(): FailureInfo = FailureInfo(
        code = "SIMULATED_ADAPTER_LOAD_FAILURE",
        domain = FailureDomain.ADAPTER,
        confidence = EvidenceConfidence.OBSERVED,
        detail = "Controlled fixture: no real adapter was loaded.",
    )

    @Test
    fun `synthetic passed result is not qualification evidence`() {
        val result = result(AttemptOutcome.PASSED, EvidenceOrigin.SYNTHETIC)

        assertFalse(isEligibleForDeviceQualification(result))
    }

    @Test
    fun `synthetic failed result is not qualification evidence`() {
        val result = result(
            outcome = AttemptOutcome.FAILED,
            origin = EvidenceOrigin.SYNTHETIC,
            failure = failure(),
        )

        assertFalse(isEligibleForDeviceQualification(result))
    }

    @Test
    fun `imported unverified result is not qualification evidence even when passed`() {
        val result = result(AttemptOutcome.PASSED, EvidenceOrigin.IMPORTED_UNVERIFIED)

        assertFalse(isEligibleForDeviceQualification(result))
    }

    @Test
    fun `cancelled device measurement is rejected`() {
        val result = result(AttemptOutcome.CANCELLED, EvidenceOrigin.DEVICE_MEASUREMENT)

        assertFalse(isEligibleForDeviceQualification(result))
    }

    @Test
    fun `interrupted device measurement is rejected`() {
        val result = result(AttemptOutcome.INTERRUPTED, EvidenceOrigin.DEVICE_MEASUREMENT)

        assertFalse(isEligibleForDeviceQualification(result))
    }

    @Test
    fun `skipped device measurement is rejected`() {
        val result = result(AttemptOutcome.SKIPPED, EvidenceOrigin.DEVICE_MEASUREMENT)

        assertFalse(isEligibleForDeviceQualification(result))
    }

    @Test
    fun `invalid device measurement is rejected`() {
        val result = result(
            outcome = AttemptOutcome.PASSED,
            origin = EvidenceOrigin.DEVICE_MEASUREMENT,
            validity = ResultValidity.INVALID,
        )

        assertFalse(isEligibleForDeviceQualification(result))
    }

    @Test
    fun `unknown validity device measurement is rejected`() {
        val result = result(
            outcome = AttemptOutcome.PASSED,
            origin = EvidenceOrigin.DEVICE_MEASUREMENT,
            validity = ResultValidity.UNKNOWN,
        )

        assertFalse(isEligibleForDeviceQualification(result))
    }

    @Test
    fun `completed device measurements pass only the guard`() {
        // The guard returns a boolean precondition. It does not create a verdict, does not
        // hide a model and does not decide that a failure belongs to the model.
        val passed = result(AttemptOutcome.PASSED, EvidenceOrigin.DEVICE_MEASUREMENT)
        val failed = result(
            outcome = AttemptOutcome.FAILED,
            origin = EvidenceOrigin.DEVICE_MEASUREMENT,
            failure = failure(),
        )
        val timedOut = result(AttemptOutcome.TIMED_OUT, EvidenceOrigin.DEVICE_MEASUREMENT)

        assertTrue(isEligibleForDeviceQualification(passed))
        assertTrue(isEligibleForDeviceQualification(failed))
        assertTrue(isEligibleForDeviceQualification(timedOut))
    }
}
