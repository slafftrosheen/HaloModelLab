package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.domain.EvidenceOrigin

data class FailureSignature(val code: String, val domain: String, val stage: String)

data class ComparableRun(
    val sessionId: String,
    val origin: EvidenceOrigin,
    val scopeDigestSha256: String?,
    val policyDigestSha256: String?,
    val sourceIdentitiesVerified: Boolean,
    val failures: Set<FailureSignature>,
)

enum class ComparisonStatus { COMPATIBLE, INCOMPARABLE }

data class SessionComparison(
    val status: ComparisonStatus,
    val reasonCode: String,
    val newFailures: Set<FailureSignature> = emptySet(),
    val resolvedFailures: Set<FailureSignature> = emptySet(),
    val persistentFailures: Set<FailureSignature> = emptySet(),
)

/** Compares only exact, verified device scopes. It deliberately makes no regression claim. */
object ComparisonBuilder {
    fun compare(current: ComparableRun, previous: ComparableRun): SessionComparison {
        val reason = when {
            current.sessionId == previous.sessionId -> "SAME_SESSION"
            current.origin != EvidenceOrigin.DEVICE_MEASUREMENT || previous.origin != EvidenceOrigin.DEVICE_MEASUREMENT -> "NON_DEVICE_EVIDENCE"
            !current.sourceIdentitiesVerified || !previous.sourceIdentitiesVerified -> "SOURCE_IDENTITY_UNVERIFIED"
            current.scopeDigestSha256.isNullOrBlank() || previous.scopeDigestSha256.isNullOrBlank() -> "SCOPE_IDENTITY_UNKNOWN"
            current.scopeDigestSha256 != previous.scopeDigestSha256 -> "EXECUTION_SCOPE_CHANGED"
            current.policyDigestSha256.isNullOrBlank() || previous.policyDigestSha256.isNullOrBlank() -> "POLICY_IDENTITY_UNKNOWN"
            current.policyDigestSha256 != previous.policyDigestSha256 -> "QUALIFICATION_POLICY_CHANGED"
            else -> null
        }
        if (reason != null) return SessionComparison(ComparisonStatus.INCOMPARABLE, reason)
        return SessionComparison(
            status = ComparisonStatus.COMPATIBLE,
            reasonCode = "EXACT_SCOPE_AND_POLICY_MATCH",
            newFailures = current.failures - previous.failures,
            resolvedFailures = previous.failures - current.failures,
            persistentFailures = current.failures intersect previous.failures,
        )
    }
}
