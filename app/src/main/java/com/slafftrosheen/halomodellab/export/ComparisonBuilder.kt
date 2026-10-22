package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.data.SessionExportSnapshot
import com.slafftrosheen.halomodellab.domain.ResultValidity
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
    val metrics: Map<MetricKey, ComparableMetric> = emptyMap(),
)

data class MetricKey(
    val name: String,
    val unit: String,
    val scope: String,
    val subjectId: String = "",
    val scenarioId: String = "",
    val role: String = "",
    val requestedBackend: String = "",
)

data class ComparableMetric(val median: Double?, val unavailableReason: String?)

data class MetricDelta(
    val key: MetricKey,
    val currentValue: Double?,
    val previousValue: Double?,
    val delta: Double?,
    val unavailableReason: String?,
)

enum class ComparisonStatus { COMPATIBLE, INCOMPARABLE }

data class SessionComparison(
    val status: ComparisonStatus,
    val reasonCode: String,
    val newFailures: Set<FailureSignature> = emptySet(),
    val resolvedFailures: Set<FailureSignature> = emptySet(),
    val persistentFailures: Set<FailureSignature> = emptySet(),
    val metricDeltas: List<MetricDelta> = emptyList(),
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
        val attemptContext = snapshot.detail.queue.flatMap { item ->
            item.attempts.map { it.attemptId to (it.validity to item) }
        }.toMap()
        val metrics = snapshot.detail.telemetry
            .filter { it.origin == EvidenceOrigin.DEVICE_MEASUREMENT && it.attemptId != null }
            .groupBy { sample ->
                val item = sample.attemptId?.let { attemptContext[it]?.second }
                MetricKey(
                    name = sample.name,
                    unit = sample.unit,
                    scope = sample.scope,
                    subjectId = item?.subjectId.orEmpty(),
                    scenarioId = item?.scenarioId.orEmpty(),
                    role = item?.role?.name.orEmpty(),
                    requestedBackend = item?.requestedBackend?.name.orEmpty(),
                )
            }
            .mapValues { (_, samples) ->
                val trusted = samples.filter { sample ->
                    sample.value?.isFinite() == true && sample.unavailableReason == null &&
                        sample.attemptId?.let { attemptContext[it]?.first == ResultValidity.VALID } == true
                }.mapNotNull { it.value }.sorted()
                val median = when {
                    trusted.isEmpty() -> null
                    trusted.size % 2 == 1 -> trusted[trusted.size / 2]
                    else -> trusted[trusted.size / 2 - 1] / 2.0 + trusted[trusted.size / 2] / 2.0
                }
                ComparableMetric(
                    median = median,
                    unavailableReason = if (median == null) {
                        samples.mapNotNull { it.unavailableReason }.distinct().singleOrNull()
                            ?: "NO_VALID_DEVICE_MEASUREMENT"
                    } else null,
                )
            }
        return ComparableRun(
            sessionId = snapshot.detail.summary.sessionId,
            origin = snapshot.detail.summary.origin,
            scopeDigestSha256 = scope,
            policyDigestSha256 = evidence?.plan?.qualificationPolicySha256?.takeIf(SHA256::matches),
            sourceIdentitiesVerified = sourceVerified,
            failures = failures,
            metrics = metrics,
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
            metricDeltas = (current.metrics.keys + previous.metrics.keys).distinct()
                .sortedWith(compareBy<MetricKey> { it.name }.thenBy { it.unit }.thenBy { it.scope }
                    .thenBy { it.subjectId }.thenBy { it.scenarioId }.thenBy { it.role }.thenBy { it.requestedBackend })
                .map { key ->
                    val now = current.metrics[key]?.median
                    val before = previous.metrics[key]?.median
                    val measuredDelta = if (now != null && before != null) now - before else null
                    val missingReason = when {
                        measuredDelta != null && !measuredDelta.isFinite() -> "DELTA_NON_FINITE"
                        now == null && before == null -> listOfNotNull(
                            current.metrics[key]?.unavailableReason?.let { "CURRENT:$it" },
                            previous.metrics[key]?.unavailableReason?.let { "PREVIOUS:$it" },
                        ).joinToString(";").ifBlank { "MEASUREMENT_UNKNOWN" }
                        now == null -> current.metrics[key]?.unavailableReason?.let { "CURRENT:$it" } ?: "CURRENT_MEASUREMENT_UNKNOWN"
                        before == null -> previous.metrics[key]?.unavailableReason?.let { "PREVIOUS:$it" } ?: "PREVIOUS_MEASUREMENT_UNKNOWN"
                        else -> null
                    }
                    MetricDelta(
                        key = key,
                        currentValue = now,
                        previousValue = before,
                        delta = measuredDelta?.takeIf(Double::isFinite),
                        unavailableReason = missingReason,
                    )
                },
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
