package com.slafftrosheen.halomodellab.qualification

import androidx.room.withTransaction
import com.slafftrosheen.halomodellab.data.LabDatabase
import com.slafftrosheen.halomodellab.data.QualificationEntity
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
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

    suspend fun persistProjection(sessionId: String, decision: QualificationDecision): Boolean {
        val scope = decision.exactScopeDigestSha256 ?: return false
        if (!SHA256.matches(scope) || !SHA256.matches(decision.policyDigestSha256)) return false
        if (decision.mayHideExactScope && decision.status != QualificationStatus.REJECTED_FOR_PROFILE) return false
        val evidence = encodeEvidence(decision)
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

    private fun encodeEvidence(decision: QualificationDecision): String {
        val attemptIds = decision.eligibleAttemptIds.distinct()
        val buffer = Buffer()
        val writer = JsonWriter.of(buffer)
        writer.serializeNulls = true
        writer.beginObject()
        writer.name("schemaVersion").value(1)
        writer.name("mayHideExactScope").value(decision.mayHideExactScope)
        writer.name("reasonCodes").beginArray()
        decision.reasonCodes.take(MAX_REASONS).forEach { writer.value(it) }
        writer.endArray()
        writer.name("attemptIds").beginArray()
        attemptIds.take(MAX_ATTEMPT_IDS).forEach { writer.value(it) }
        writer.endArray()
        writer.name("truncated").value(
            decision.reasonCodes.size > MAX_REASONS || attemptIds.size > MAX_ATTEMPT_IDS,
        )
        writer.endObject()
        writer.close()
        return buffer.readUtf8().also { require(it.length <= MAX_EVIDENCE_CHARS) }
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
        const val MAX_HISTORY_ROWS = 500
        const val MAX_REASONS = 16
        const val MAX_ATTEMPT_IDS = 24
        const val MAX_EVIDENCE_CHARS = 8_192
    }
}
