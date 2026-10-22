package com.slafftrosheen.halomodellab.qualification

import com.slafftrosheen.halomodellab.bridge.BridgeEventKind
import com.slafftrosheen.halomodellab.bridge.BridgeEventPayload
import com.slafftrosheen.halomodellab.catalog.FastSmokePlanFactory
import com.slafftrosheen.halomodellab.profiles.TextProfileRegistry
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.ExecutionScope
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.RealCasePlan
import com.slafftrosheen.halomodellab.domain.ResultValidity

/** Maps only replayed terminal evidence; absent identities and assertions remain unknown. */
object QualificationObservationMapper {
    fun fromTerminal(
        event: BridgeEventPayload,
        casePlan: RealCasePlan,
        scope: ExecutionScope?,
        expectedText: String?,
        metricName: String? = null,
        metricValue: Double? = null,
        metricUnit: String? = null,
    ): QualificationObservation? {
        if (event.kind != BridgeEventKind.ATTEMPT_TERMINAL || event.attemptId == null ||
            event.outcome == null || event.validity == null
        ) return null
        val expectedArtifactSha = casePlan.artifactManifest?.parts
            ?.firstOrNull { it.relativeName.endsWith(".litertlm") }?.sha256
        val verifiedScope = scope?.takeIf {
            it == casePlan.executionScope && it.mayReuseExactDeviceEvidence() &&
                expectedArtifactSha != null && event.artifactSha256 == expectedArtifactSha &&
                event.effectiveBackendCode == it.effectiveBackend?.name && event.runtimeVersion == it.runtimeVersion
        }
        val expectedFixtureSha = TextProfileRegistry.resolve(casePlan.suiteId, casePlan.suiteVersion)
            ?.fixtureManifestSha256()
        val fixtureKnown = casePlan.fixtureManifestSha256 != null &&
            casePlan.fixtureManifestSha256 == expectedFixtureSha &&
            casePlan.suiteId == FastSmokePlanFactory.SUITE_ID &&
            casePlan.suiteVersion == FastSmokePlanFactory.SUITE_VERSION &&
            casePlan.caseId == FastSmokePlanFactory.CASE_ID
        val completeOutput = expectedText != null && event.outputTruncated == false && event.outputPreview != null
        val outputMatches = completeOutput && event.outputPreview?.trim() == expectedText
        val fixtureMismatch = fixtureKnown && completeOutput && !outputMatches
        val assertion = when {
            event.validity == ResultValidity.VALID && event.outcome == AttemptOutcome.PASSED && outputMatches -> CheckAssertion.PASS
            event.validity == ResultValidity.VALID && fixtureMismatch &&
                (event.outcome == AttemptOutcome.PASSED ||
                    (event.outcome == AttemptOutcome.FAILED && event.failureDomain == FailureDomain.MODEL)) -> CheckAssertion.FAIL
            else -> CheckAssertion.UNKNOWN
        }
        val mappedOutcome = if (fixtureMismatch && event.outcome == AttemptOutcome.PASSED) {
            AttemptOutcome.FAILED
        } else {
            event.outcome
        }
        val trustedMetric = verifiedScope != null && mappedOutcome == AttemptOutcome.PASSED &&
            event.validity == ResultValidity.VALID && metricName != null && metricValue?.isFinite() == true && metricUnit != null
        return QualificationObservation(
            attemptId = event.attemptId,
            scopeDigestSha256 = verifiedScope?.canonicalSha256(),
            policyDigestSha256 = verifiedScope?.qualificationPolicySha256,
            checkId = casePlan.caseId,
            origin = EvidenceOrigin.DEVICE_MEASUREMENT,
            validity = event.validity,
            outcome = mappedOutcome,
            workerInstanceId = event.workerInstanceId,
            fixtureManifestKnown = fixtureKnown,
            harnessControlsKnown = fixtureKnown && event.validity == ResultValidity.VALID,
            assertion = assertion,
            failureDomain = if (fixtureMismatch && event.outcome == AttemptOutcome.PASSED) {
                FailureDomain.MODEL
            } else {
                event.failureDomain ?: FailureDomain.UNKNOWN
            },
            metricName = metricName.takeIf { trustedMetric },
            metricValue = metricValue.takeIf { trustedMetric },
            metricUnit = metricUnit.takeIf { trustedMetric },
            metricTrusted = trustedMetric,
        )
    }
}
