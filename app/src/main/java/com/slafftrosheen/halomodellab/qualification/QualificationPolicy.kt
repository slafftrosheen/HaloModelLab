package com.slafftrosheen.halomodellab.qualification

import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.ResultValidity

private val SHA256 = Regex("[0-9a-f]{64}")
private val SAFE_CODE = Regex("[A-Za-z0-9._:-]{1,96}")
private val SAFE_ID = Regex("[A-Za-z0-9._:-]{1,160}")

enum class CheckAssertion { PASS, FAIL, UNKNOWN }
enum class QualificationStatus {
    UNTESTED,
    PARTIAL,
    QUALIFIED,
    REJECTED_FOR_PROFILE,
    INCONCLUSIVE,
    STALE,
    UNSUPPORTED_CONFIGURATION,
}

data class PerformanceThreshold(val metricName: String, val unit: String, val maximumValue: Double) {
    init {
        require(metricName.isNotBlank() && unit.isNotBlank())
        require(maximumValue.isFinite() && maximumValue > 0.0)
    }
}

data class QualificationPolicy(
    val policyDigestSha256: String,
    val requiredCheckIds: Set<String>,
    val performanceThresholds: List<PerformanceThreshold> = emptyList(),
    val minimumStructuralFailures: Int = 2,
    val minimumPerformanceSamples: Int = 3,
) {
    init {
        require(SHA256.matches(policyDigestSha256))
        require(requiredCheckIds.isNotEmpty() && requiredCheckIds.size <= 64)
        require(requiredCheckIds.all { SAFE_CODE.matches(it) })
        require(performanceThresholds.map { it.metricName to it.unit }.distinct().size == performanceThresholds.size)
        require(minimumStructuralFailures >= 2)
        require(minimumPerformanceSamples >= 3)
    }
}

/** One immutable observation. Attribution and assertion are recorded separately from outcome. */
data class QualificationObservation(
    val attemptId: String,
    val scopeDigestSha256: String?,
    val policyDigestSha256: String?,
    val checkId: String,
    val origin: EvidenceOrigin,
    val validity: ResultValidity,
    val outcome: AttemptOutcome,
    val workerInstanceId: String?,
    val fixtureManifestKnown: Boolean,
    val harnessControlsKnown: Boolean,
    val assertion: CheckAssertion,
    val failureDomain: FailureDomain,
    val metricName: String? = null,
    val metricValue: Double? = null,
    val metricUnit: String? = null,
    val metricTrusted: Boolean = false,
) {
    init {
        require(SAFE_ID.matches(attemptId))
        require(SAFE_CODE.matches(checkId))
        require(scopeDigestSha256 == null || SHA256.matches(scopeDigestSha256))
        require(policyDigestSha256 == null || SHA256.matches(policyDigestSha256))
        require(workerInstanceId == null || SAFE_ID.matches(workerInstanceId))
        require(metricName == null || SAFE_CODE.matches(metricName))
        require(metricUnit == null || SAFE_CODE.matches(metricUnit))
        require(metricValue == null || metricValue.isFinite())
        require((metricName == null) == (metricValue == null) && (metricName == null) == (metricUnit == null))
    }
}

data class QualificationDecision(
    val status: QualificationStatus,
    val exactScopeDigestSha256: String?,
    val policyDigestSha256: String,
    val eligibleAttemptIds: List<String>,
    val reasonCodes: List<String>,
    val mayHideExactScope: Boolean,
)

/** Pure deterministic policy. It cannot delete evidence or mutate Halo's selected roles. */
object QualificationEvaluator {
    fun evaluate(
        currentScopeDigestSha256: String?,
        currentPolicy: QualificationPolicy,
        observations: List<QualificationObservation>,
        scopeIdentityVerifiedForQualification: Boolean = false,
        unsupportedConfiguration: Boolean = false,
    ): QualificationDecision {
        if (unsupportedConfiguration) return decision(
            QualificationStatus.UNSUPPORTED_CONFIGURATION, currentScopeDigestSha256,
            currentPolicy, emptyList(), listOf("CONFIGURATION_UNSUPPORTED"), false,
        )
        if (currentScopeDigestSha256 == null || !SHA256.matches(currentScopeDigestSha256)) return decision(
            QualificationStatus.INCONCLUSIVE, null, currentPolicy, emptyList(), listOf("SCOPE_IDENTITY_UNKNOWN"), false,
        )
        if (!scopeIdentityVerifiedForQualification) return decision(
            QualificationStatus.INCONCLUSIVE, currentScopeDigestSha256, currentPolicy, emptyList(),
            listOf("SCOPE_IDENTITY_INELIGIBLE"), false,
        )

        val sameScope = observations.filter { it.scopeDigestSha256 == currentScopeDigestSha256 }
        val exact = sameScope.filter { it.policyDigestSha256 == currentPolicy.policyDigestSha256 }
        if (exact.isEmpty()) {
            val status = if (observations.isEmpty()) QualificationStatus.UNTESTED else QualificationStatus.STALE
            return decision(status, currentScopeDigestSha256, currentPolicy, emptyList(), listOf(if (status == QualificationStatus.STALE) "SCOPE_CHANGED" else "NO_EVIDENCE"), false)
        }
        val unique = exact.distinctBy { it.attemptId }
        val eligible = unique.filter { observation ->
            observation.origin == EvidenceOrigin.DEVICE_MEASUREMENT &&
                observation.validity == ResultValidity.VALID &&
                observation.outcome in setOf(AttemptOutcome.PASSED, AttemptOutcome.FAILED, AttemptOutcome.TIMED_OUT)
        }
        if (eligible.isEmpty()) return decision(
            QualificationStatus.INCONCLUSIVE, currentScopeDigestSha256, currentPolicy, emptyList(),
            listOf("NO_ELIGIBLE_DEVICE_EVIDENCE"), false,
        )

        val completedChecks = eligible.filter { it.assertion != CheckAssertion.UNKNOWN }.map { it.checkId }.toSet()
        if (!completedChecks.containsAll(currentPolicy.requiredCheckIds)) return decision(
            QualificationStatus.PARTIAL, currentScopeDigestSha256, currentPolicy,
            eligible.map { it.attemptId }, listOf("REQUIRED_CHECKS_INCOMPLETE"), false,
        )

        val conflictingChecks = currentPolicy.requiredCheckIds.filter { checkId ->
            eligible.any { it.checkId == checkId && it.assertion == CheckAssertion.PASS } &&
                eligible.any { it.checkId == checkId && it.assertion == CheckAssertion.FAIL }
        }
        if (conflictingChecks.isNotEmpty()) return decision(
            QualificationStatus.INCONCLUSIVE, currentScopeDigestSha256, currentPolicy,
            eligible.filter { it.checkId in conflictingChecks }.map { it.attemptId },
            listOf("CONFLICTING_ASSERTIONS"), false,
        )

        val structuralGroup = eligible.asSequence().filter {
            it.assertion == CheckAssertion.FAIL &&
                it.failureDomain == FailureDomain.MODEL &&
                it.fixtureManifestKnown && it.harnessControlsKnown &&
                it.workerInstanceId != null
        }.groupBy { it.checkId }.values.firstOrNull { group ->
            group.mapNotNull { it.workerInstanceId }.distinct().size >= currentPolicy.minimumStructuralFailures
        }
        if (structuralGroup != null) return decision(
            QualificationStatus.REJECTED_FOR_PROFILE, currentScopeDigestSha256, currentPolicy,
            structuralGroup.map { it.attemptId }, listOf("REPEATED_MODEL_ASSERTION_FAILURE"), true,
        )

        val performance = currentPolicy.performanceThresholds.firstNotNullOfOrNull { threshold ->
            val samples = eligible.filter {
                it.checkId in currentPolicy.requiredCheckIds &&
                    it.metricName == threshold.metricName && it.metricUnit == threshold.unit &&
                    it.metricValue != null && it.metricTrusted &&
                    it.failureDomain !in INELIGIBLE_PERFORMANCE_DOMAINS
            }
            val over = samples.filter { it.metricValue!! > threshold.maximumValue }
            if (over.size >= currentPolicy.minimumPerformanceSamples) {
                val within = samples.any { it.metricValue!! <= threshold.maximumValue }
                when {
                    within -> QualificationDecision(
                        QualificationStatus.INCONCLUSIVE, currentScopeDigestSha256,
                        currentPolicy.policyDigestSha256, samples.map { it.attemptId },
                        listOf("CONFLICTING_PERFORMANCE_SAMPLES"), false,
                    )
                    else -> QualificationDecision(
                        QualificationStatus.REJECTED_FOR_PROFILE, currentScopeDigestSha256,
                        currentPolicy.policyDigestSha256, over.map { it.attemptId },
                        listOf("PERFORMANCE_THRESHOLD_EXCEEDED"), true,
                    )
                }
            } else null
        }
        if (performance != null) return performance

        val allPassed = currentPolicy.requiredCheckIds.all { checkId ->
            eligible.any { it.checkId == checkId && it.assertion == CheckAssertion.PASS }
        }
        return if (allPassed) decision(
            QualificationStatus.QUALIFIED, currentScopeDigestSha256, currentPolicy,
            eligible.map { it.attemptId }, listOf("REQUIRED_CHECKS_PASSED"), false,
        ) else decision(
            QualificationStatus.INCONCLUSIVE, currentScopeDigestSha256, currentPolicy,
            eligible.map { it.attemptId }, listOf("FAILURE_NOT_ATTRIBUTED_TO_MODEL"), false,
        )
    }

    private fun decision(
        status: QualificationStatus,
        scope: String?,
        policy: QualificationPolicy,
        attempts: List<String>,
        reasons: List<String>,
        hide: Boolean,
    ) = QualificationDecision(status, scope, policy.policyDigestSha256, attempts.distinct(), reasons, hide)

    private val INELIGIBLE_PERFORMANCE_DOMAINS = setOf(
        FailureDomain.ADAPTER, FailureDomain.RUNTIME, FailureDomain.DEVICE,
        FailureDomain.ENVIRONMENT, FailureDomain.HARNESS, FailureDomain.UNKNOWN,
    )
}
