package com.slafftrosheen.halomodellab.qualification

import androidx.room.withTransaction
import com.slafftrosheen.halomodellab.data.LabDatabase
import com.slafftrosheen.halomodellab.data.QualificationEntity
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.runner.LabClock
import com.squareup.moshi.JsonWriter
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import okio.Buffer

/** Stores local derived projections while keeping the attempt evidence as the source of truth. */
class QualificationRepository(
    private val database: LabDatabase,
    private val clock: LabClock,
) {
    fun observeHistory(): Flow<List<QualificationEntity>> = database.labDao().observeQualificationHistory(MAX_HISTORY_ROWS)

    suspend fun persistProjection(
        sessionId: String,
        decision: QualificationDecision,
        observations: List<QualificationObservation> = emptyList(),
    ): Boolean {
        val scope = decision.exactScopeDigestSha256 ?: return false
        if (!SHA256.matches(scope) || !SHA256.matches(decision.policyDigestSha256)) return false
        if (decision.mayHideExactScope && decision.status != QualificationStatus.REJECTED_FOR_PROFILE) return false
        val evidence = encodeEvidence(decision, observations)
        return database.withTransaction {
            val session = database.labDao().sessionById(sessionId) ?: return@withTransaction false
            if (session.origin != EvidenceOrigin.DEVICE_MEASUREMENT.name) return@withTransaction false
            val identity = "$sessionId|$scope|${decision.policyDigestSha256}"
            database.labDao().upsertQualificationProjection(
                QualificationEntity(
                    id = UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString(),
                    sessionId = sessionId,
                    scopeDigestSha256 = scope,
                    policyDigestSha256 = decision.policyDigestSha256,
                    status = decision.status.name,
                    evidenceJson = evidence,
                    updatedAtWallTimeMs = clock.wallTimeMs(),
                ),
            )
            true
        }
    }

    /** Combines only exact-scope observations from prior device projections, then writes a local view. */
    suspend fun recordObservation(
        sessionId: String,
        observation: QualificationObservation,
        policy: QualificationPolicy,
    ): QualificationDecision? {
        val scope = observation.scopeDigestSha256 ?: return null
        if (!SHA256.matches(scope) || observation.policyDigestSha256 != policy.policyDigestSha256) return null
        val priorRows = database.labDao().qualificationsForExactScope(scope, policy.policyDigestSha256, MAX_SCOPE_HISTORY_ROWS)
        val decoded = priorRows.map(::decodeObservations)
        if (decoded.any { it.truncated }) return null
        val observations = (decoded.flatMap { it.observations } + observation)
            .distinctBy { it.attemptId }
        if (observations.size > MAX_SCOPE_OBSERVATIONS) return null
        val decision = QualificationEvaluator.evaluate(
            currentScopeDigestSha256 = scope,
            currentPolicy = policy,
            observations = observations,
            scopeIdentityVerifiedForQualification = true,
        )
        persistProjection(sessionId, decision, observations)
        return decision
    }

    private fun encodeEvidence(decision: QualificationDecision, observations: List<QualificationObservation>): String {
        val attemptIds = decision.eligibleAttemptIds.distinct()
        val buffer = Buffer()
        val writer = JsonWriter.of(buffer)
        writer.serializeNulls = true
        writer.beginObject()
        writer.name("schemaVersion").value(2)
        writer.name("mayHideExactScope").value(decision.mayHideExactScope)
        writer.name("reasonCodes").beginArray()
        decision.reasonCodes.take(MAX_REASONS).forEach { writer.value(it) }
        writer.endArray()
        writer.name("attemptIds").beginArray()
        attemptIds.take(MAX_ATTEMPT_IDS).forEach { writer.value(it) }
        writer.endArray()
        writer.name("observationHistoryPresent").value(observations.isNotEmpty())
        writer.name("observations").beginArray()
        observations.take(MAX_SCOPE_OBSERVATIONS).forEach { observation ->
            writer.beginArray()
                .value(observation.attemptId)
                .value(observation.checkId)
                .value(observation.origin.name)
                .value(observation.validity.name)
                .value(observation.outcome.name)
                .value(observation.workerInstanceId)
                .value(observation.fixtureManifestKnown)
                .value(observation.harnessControlsKnown)
                .value(observation.assertion.name)
                .value(observation.failureDomain.name)
                .value(observation.metricName)
                .value(observation.metricValue)
                .value(observation.metricUnit)
                .value(observation.metricTrusted)
            writer.endArray()
        }
        writer.endArray()
        writer.name("truncated").value(
            decision.reasonCodes.size > MAX_REASONS || attemptIds.size > MAX_ATTEMPT_IDS ||
                observations.size > MAX_SCOPE_OBSERVATIONS,
        )
        writer.endObject()
        writer.close()
        return buffer.readUtf8().also { require(it.length <= MAX_EVIDENCE_CHARS) }
    }

    private fun decodeObservations(entity: QualificationEntity): DecodedObservations = runCatching {
        val root = org.json.JSONObject(entity.evidenceJson)
        if (root.optInt("schemaVersion", -1) != 2) {
            return@runCatching DecodedObservations(emptyList(), false)
        }
        if (root.optBoolean("truncated", false)) {
            return@runCatching DecodedObservations(emptyList(), true)
        }
        if (!root.optBoolean("observationHistoryPresent", false)) {
            return@runCatching DecodedObservations(emptyList(), false)
        }
        val rows = root.optJSONArray("observations") ?: return@runCatching DecodedObservations(emptyList(), true)
        if (rows.length() > MAX_SCOPE_OBSERVATIONS) return@runCatching DecodedObservations(emptyList(), true)
        val mapped = mutableListOf<QualificationObservation>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONArray(index) ?: return@runCatching DecodedObservations(emptyList(), true)
            if (row.length() != OBSERVATION_FIELDS) return@runCatching DecodedObservations(emptyList(), true)
            mapped += QualificationObservation(
                attemptId = row.getString(0),
                scopeDigestSha256 = entity.scopeDigestSha256,
                policyDigestSha256 = entity.policyDigestSha256,
                checkId = row.getString(1),
                origin = EvidenceOrigin.valueOf(row.getString(2)),
                validity = ResultValidity.valueOf(row.getString(3)),
                outcome = AttemptOutcome.valueOf(row.getString(4)),
                workerInstanceId = row.optString(5).takeIf { it.isNotBlank() && it != "null" },
                fixtureManifestKnown = row.getBoolean(6),
                harnessControlsKnown = row.getBoolean(7),
                assertion = CheckAssertion.valueOf(row.getString(8)),
                failureDomain = FailureDomain.valueOf(row.getString(9)),
                metricName = row.optString(10).takeIf { it.isNotBlank() && it != "null" },
                metricValue = if (row.isNull(11)) null else row.getDouble(11),
                metricUnit = row.optString(12).takeIf { it.isNotBlank() && it != "null" },
                metricTrusted = row.optBoolean(13, false),
            )
        }
        DecodedObservations(mapped, mapped.isEmpty())
    }.getOrElse { DecodedObservations(emptyList(), true) }

    private data class DecodedObservations(val observations: List<QualificationObservation>, val truncated: Boolean)

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
        const val MAX_HISTORY_ROWS = 500
        const val MAX_SCOPE_HISTORY_ROWS = 128
        const val MAX_SCOPE_OBSERVATIONS = 32
        const val OBSERVATION_FIELDS = 14
        const val MAX_REASONS = 16
        const val MAX_ATTEMPT_IDS = 24
        const val MAX_EVIDENCE_CHARS = 8_192
    }
}
