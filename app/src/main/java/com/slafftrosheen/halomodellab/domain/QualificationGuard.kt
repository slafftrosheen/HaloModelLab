package com.slafftrosheen.halomodellab.domain

/**
 * Necessary precondition for any future device-qualification verdict.
 *
 * This is a gate only. It does NOT produce a verdict, does NOT hide a model, does NOT
 * prove sufficient coverage and does NOT infer a cause from a failure. In v0 no component
 * in this application creates a negative verdict for a real catalog entry.
 */
fun isEligibleForDeviceQualification(result: AttemptResult): Boolean =
    result.origin == EvidenceOrigin.DEVICE_MEASUREMENT &&
        result.validity == ResultValidity.VALID &&
        result.outcome in setOf(
            AttemptOutcome.PASSED,
            AttemptOutcome.FAILED,
            AttemptOutcome.TIMED_OUT,
        )
