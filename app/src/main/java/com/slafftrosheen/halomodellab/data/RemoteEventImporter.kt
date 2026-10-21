package com.slafftrosheen.halomodellab.data

import androidx.room.withTransaction
import com.slafftrosheen.halomodellab.bridge.BridgeLimits
import com.slafftrosheen.halomodellab.bridge.BridgeRemoteEvent
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RemoteRunBindingResult {
    data object Bound : RemoteRunBindingResult
    data object SessionBindingMissing : RemoteRunBindingResult
    data object ConflictingRunIdentity : RemoteRunBindingResult
}

sealed interface RemoteEventPageResult {
    data class Imported(
        val acknowledgedSequence: Long,
        val insertedCount: Int,
        val duplicateCount: Int,
    ) : RemoteEventPageResult

    data class Gap(val expectedSequence: Long, val receivedSequence: Long) : RemoteEventPageResult
    data class ConflictingPayload(val sequence: Long) : RemoteEventPageResult
    data class Rejected(val code: String) : RemoteEventPageResult
}

/**
 * Commits remote facts and the highest contiguous cursor in one transaction. A caller may
 * acknowledge only the returned cursor; retries are safe because the event key is unique.
 */
class RemoteEventImporter(
    private val database: LabDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val wallTimeMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun bindAcceptedRun(
        sessionId: String,
        serverEpoch: String,
        remoteRunId: String,
        serverInstanceId: String,
    ): RemoteRunBindingResult = withContext(ioDispatcher) {
        database.withTransaction {
            val binding = database.labDao().remoteBinding(sessionId)
                ?: return@withTransaction RemoteRunBindingResult.SessionBindingMissing
            if ((binding.serverEpoch != null && binding.serverEpoch != serverEpoch) ||
                (binding.remoteRunId != null && binding.remoteRunId != remoteRunId)
            ) {
                return@withTransaction RemoteRunBindingResult.ConflictingRunIdentity
            }
            if (database.labDao().bindRemoteRun(
                    sessionId = sessionId,
                    serverEpoch = serverEpoch,
                    remoteRunId = remoteRunId,
                    serverInstanceId = serverInstanceId,
                ) != 1
            ) {
                RemoteRunBindingResult.ConflictingRunIdentity
            } else {
                RemoteRunBindingResult.Bound
            }
        }
    }

    suspend fun importPage(
        sessionId: String,
        events: List<BridgeRemoteEvent>,
    ): RemoteEventPageResult = withContext(ioDispatcher) {
        try {
            BridgeLimits.requireEventPage(
                payloads = events.map { it.payloadJson.toByteArray(Charsets.UTF_8) },
                encodedEnvelopes = events.map { it.canonicalEnvelopeJson().toByteArray(Charsets.UTF_8) },
            )
            database.withTransaction {
                val dao = database.labDao()
                val binding = dao.remoteBinding(sessionId)
                    ?: abort(RemoteEventPageResult.Rejected("REMOTE_BINDING_MISSING"))
                val serverEpoch = binding.serverEpoch
                    ?: abort(RemoteEventPageResult.Rejected("REMOTE_RUN_NOT_ACCEPTED"))
                val remoteRunId = binding.remoteRunId
                    ?: abort(RemoteEventPageResult.Rejected("REMOTE_RUN_NOT_ACCEPTED"))

                var cursor = binding.highestImportedSequence
                var insertedCount = 0
                var duplicateCount = 0

                for (event in events) {
                    if (event.key.serverEpoch != serverEpoch || event.key.remoteRunId != remoteRunId) {
                        abort(RemoteEventPageResult.Rejected("EVENT_RUN_IDENTITY_MISMATCH"))
                    }
                    val bytes = event.payloadJson.toByteArray(Charsets.UTF_8)
                    if (!event.payloadSha256.equals(sha256(bytes), ignoreCase = false)) {
                        abort(RemoteEventPageResult.Rejected("EVENT_HASH_MISMATCH"))
                    }

                    val sequence = event.key.sequence
                    val saved = dao.remoteEvent(serverEpoch, remoteRunId, sequence)
                    if (sequence <= cursor) {
                        if (saved == null) abort(RemoteEventPageResult.Rejected("EVENT_HISTORY_MISSING"))
                        if (saved.payloadHashSha256 != event.payloadSha256) {
                            abort(RemoteEventPageResult.ConflictingPayload(sequence))
                        }
                        duplicateCount += 1
                        continue
                    }
                    if (sequence != cursor + 1L) {
                        abort(RemoteEventPageResult.Gap(cursor + 1L, sequence))
                    }
                    if (saved != null && saved.payloadHashSha256 != event.payloadSha256) {
                        abort(RemoteEventPageResult.ConflictingPayload(sequence))
                    }

                    if (saved == null) {
                        dao.insertRemoteEvent(
                            RemoteEventEntity(
                                serverEpoch = serverEpoch,
                                remoteRunId = remoteRunId,
                                remoteSequence = sequence,
                                sessionId = sessionId,
                                attemptId = event.payload.attemptId,
                                payloadHashSha256 = event.payloadSha256,
                                payloadJson = event.payloadJson,
                                receivedWallTimeMs = wallTimeMs(),
                            ),
                        )
                        insertedCount += 1
                    } else {
                        duplicateCount += 1
                    }

                    if (dao.advanceRemoteEventCursor(
                            sessionId = sessionId,
                            serverEpoch = serverEpoch,
                            remoteRunId = remoteRunId,
                            expectedSequence = cursor,
                            newSequence = sequence,
                        ) != 1
                    ) {
                        abort(RemoteEventPageResult.Rejected("EVENT_CURSOR_CONFLICT"))
                    }
                    cursor = sequence
                }

                RemoteEventPageResult.Imported(
                    acknowledgedSequence = cursor,
                    insertedCount = insertedCount,
                    duplicateCount = duplicateCount,
                )
            }
        } catch (abort: ImportAbort) {
            abort.result
        } catch (limit: IllegalArgumentException) {
            RemoteEventPageResult.Rejected(
                (limit as? com.slafftrosheen.halomodellab.bridge.BridgePayloadLimitException)?.code
                    ?: "EVENT_PAGE_INVALID",
            )
        }
    }

    private fun abort(result: RemoteEventPageResult): Nothing = throw ImportAbort(result)

    private class ImportAbort(val result: RemoteEventPageResult) : RuntimeException()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
