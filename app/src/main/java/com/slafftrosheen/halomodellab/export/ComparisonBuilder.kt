package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.data.SessionExportSnapshot
import com.squareup.moshi.JsonReader
import okio.Buffer

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
    fun fromSnapshot(snapshot: SessionExportSnapshot): ComparableRun {
        val evidence = snapshot.realEvidence
        val scopeRows = evidence?.configurations.orEmpty()
        val scopeDigests = scopeRows.map { it.scopeDigestSha256 }
        val scope = scopeDigests.distinct().singleOrNull()?.takeIf { scopeDigests.isNotEmpty() && SHA256.matches(it.orEmpty()) }
        val sourceVerified = scopeRows.isNotEmpty() && scopeRows.all { row ->
        row.executionScopeJson?.let(::hasVerifiedComparableIdentity) == true
        }
        val failures = snapshot.detail.queue.flatMap { item ->
            item.attempts.mapNotNull { attempt -> attempt.failure?.let { failure ->
                FailureSignature(failure.code, failure.domain.name, attempt.stage.name)
            } }
        }.toSet()
        return ComparableRun(
            sessionId = snapshot.detail.summary.sessionId,
            origin = snapshot.detail.summary.origin,
            scopeDigestSha256 = scope,
            policyDigestSha256 = evidence?.plan?.qualificationPolicySha256?.takeIf(SHA256::matches),
            sourceIdentitiesVerified = sourceVerified,
            failures = failures,
        )
    }

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

    private fun hasVerifiedComparableIdentity(json: String): Boolean = runCatching {
        val reader = JsonReader.of(Buffer().writeUtf8(json))
        reader.isLenient = false
        reader.beginObject()
        var verified = false
        var backend: String? = null
        var fixture: String? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "sourceIdentitiesVerified" -> verified = reader.nextBoolean()
                "effectiveBackend" -> backend = if (reader.peek() == JsonReader.Token.STRING) reader.nextString() else { reader.skipValue(); null }
                "fixtureManifestSha256" -> fixture = if (reader.peek() == JsonReader.Token.STRING) reader.nextString() else { reader.skipValue(); null }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        reader.peek() == JsonReader.Token.END_DOCUMENT && verified &&
            backend != null && backend != "UNSPECIFIED" && backend != "AUTO" && fixture?.let(SHA256::matches) == true
    }.getOrDefault(false)

    private val SHA256 = Regex("[0-9a-f]{64}")
}
