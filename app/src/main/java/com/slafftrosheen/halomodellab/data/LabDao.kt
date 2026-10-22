package com.slafftrosheen.halomodellab.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * All statements are single-purpose. Multi-step invariants live in RoomSessionRepository,
 * where they run inside one database transaction.
 */
@Dao
abstract class LabDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAttempt(attempt: AttemptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertEvent(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertTelemetry(telemetry: TelemetryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRunnerSlotIfMissing(slot: RunnerSlotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRealPlanSnapshot(snapshot: RealPlanSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertModelSnapshots(snapshots: List<ModelSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertConfigurationSnapshots(snapshots: List<ConfigurationSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRemoteBinding(binding: RemoteBindingEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRemoteEvent(event: RemoteEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertOutputArtifact(artifact: OutputArtifactEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertQualification(qualification: QualificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertQualificationProjection(qualification: QualificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAcquisitionProjection(projection: AcquisitionProjectionEntity)

    @Query("SELECT * FROM real_plan_snapshots WHERE sessionId = :sessionId")
    abstract suspend fun realPlanSnapshot(sessionId: String): RealPlanSnapshotEntity?

    @Query("SELECT * FROM model_snapshots WHERE sessionId = :sessionId ORDER BY providerId, modelId, generationId")
    abstract suspend fun modelSnapshotsForSession(sessionId: String): List<ModelSnapshotEntity>

    @Query("SELECT * FROM configuration_snapshots WHERE sessionId = :sessionId ORDER BY queueItemId")
    abstract suspend fun configurationSnapshotsForSession(sessionId: String): List<ConfigurationSnapshotEntity>

    @Query("SELECT * FROM output_artifacts WHERE sessionId = :sessionId ORDER BY id LIMIT :limit")
    abstract suspend fun outputArtifactsForSession(sessionId: String, limit: Int): List<OutputArtifactEntity>

    @Query("SELECT * FROM qualifications WHERE sessionId = :sessionId ORDER BY scopeDigestSha256, policyDigestSha256 LIMIT :limit")
    abstract suspend fun qualificationsForSession(sessionId: String, limit: Int): List<QualificationEntity>

    @Query("SELECT * FROM qualifications ORDER BY updatedAtWallTimeMs DESC, id LIMIT :limit")
    abstract fun observeQualificationHistory(limit: Int): Flow<List<QualificationEntity>>

    @Query("SELECT * FROM qualifications WHERE scopeDigestSha256 = :scopeDigest AND policyDigestSha256 = :policyDigest ORDER BY updatedAtWallTimeMs DESC LIMIT :limit")
    abstract suspend fun qualificationsForExactScope(scopeDigest: String, policyDigest: String, limit: Int): List<QualificationEntity>

    @Query("SELECT * FROM remote_events WHERE serverEpoch = :serverEpoch AND remoteRunId = :remoteRunId AND remoteSequence = :sequence")
    abstract suspend fun remoteEvent(serverEpoch: String, remoteRunId: String, sequence: Long): RemoteEventEntity?

    @Query("SELECT * FROM remote_bindings WHERE sessionId = :sessionId")
    abstract suspend fun remoteBinding(sessionId: String): RemoteBindingEntity?

    @Query("SELECT COUNT(*) FROM remote_events WHERE sessionId = :sessionId")
    abstract suspend fun remoteEventCount(sessionId: String): Int

    @Query("SELECT * FROM remote_events WHERE sessionId = :sessionId ORDER BY serverEpoch, remoteRunId, remoteSequence LIMIT :limit")
    abstract suspend fun remoteEventsForSession(sessionId: String, limit: Int): List<RemoteEventEntity>

    @Query("UPDATE remote_bindings SET serverEpoch = :serverEpoch, remoteRunId = :remoteRunId, currentServerInstanceId = :serverInstanceId WHERE sessionId = :sessionId AND (serverEpoch IS NULL OR serverEpoch = :serverEpoch) AND (remoteRunId IS NULL OR remoteRunId = :remoteRunId)")
    abstract suspend fun bindRemoteRun(
        sessionId: String,
        serverEpoch: String,
        remoteRunId: String,
        serverInstanceId: String,
    ): Int

    @Query("UPDATE remote_bindings SET highestImportedSequence = :newSequence WHERE sessionId = :sessionId AND serverEpoch = :serverEpoch AND remoteRunId = :remoteRunId AND highestImportedSequence = :expectedSequence")
    abstract suspend fun advanceRemoteEventCursor(
        sessionId: String,
        serverEpoch: String,
        remoteRunId: String,
        expectedSequence: Long,
        newSequence: Long,
    ): Int

    @Query("SELECT * FROM remote_events WHERE serverEpoch = :serverEpoch AND remoteRunId = :remoteRunId AND sessionId = :sessionId AND remoteSequence > :afterSequence ORDER BY remoteSequence LIMIT :limit")
    abstract suspend fun remoteEventsAfter(
        sessionId: String,
        serverEpoch: String,
        remoteRunId: String,
        afterSequence: Long,
        limit: Int,
    ): List<RemoteEventEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    abstract suspend fun sessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE state IN (:states) ORDER BY createdAtWallTimeMs DESC LIMIT 1")
    abstract suspend fun findOpenSession(states: List<String>): SessionEntity?

    @Query("SELECT * FROM sessions WHERE state IN (:states) ORDER BY createdAtWallTimeMs DESC LIMIT 1")
    abstract fun observeOpenSession(states: List<String>): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE state IN (:states) ORDER BY createdAtWallTimeMs")
    abstract suspend fun sessionsInStates(states: List<String>): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    abstract fun observeSession(sessionId: String): Flow<SessionEntity?>

    @Query(
        "SELECT s.id AS id, s.state AS state, s.origin AS origin, s.engineId AS engineId, " +
            "s.profileId AS profileId, s.createdAtWallTimeMs AS createdAtWallTimeMs, " +
            "s.finishedWallTimeMs AS finishedWallTimeMs, " +
            "(SELECT COUNT(*) FROM queue_items q WHERE q.sessionId = s.id) AS itemCount, " +
            "(SELECT COUNT(*) FROM queue_items q WHERE q.sessionId = s.id AND q.state IN ('DONE', 'SKIPPED')) AS completedCount " +
            "FROM sessions s ORDER BY s.createdAtWallTimeMs DESC",
    )
    abstract fun observeSummaryRows(): Flow<List<SessionSummaryRow>>

    @Query("SELECT * FROM queue_items WHERE sessionId = :sessionId ORDER BY ordinal")
    abstract fun observeQueueItems(sessionId: String): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE sessionId = :sessionId ORDER BY ordinal")
    abstract suspend fun queueItems(sessionId: String): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE id = :itemId")
    abstract suspend fun queueItemById(itemId: String): QueueItemEntity?

    @Query("SELECT * FROM queue_items WHERE sessionId = :sessionId AND state = :state ORDER BY ordinal LIMIT 1")
    abstract suspend fun findFirstItemInState(sessionId: String, state: String): QueueItemEntity?

    @Query("SELECT COUNT(*) FROM queue_items WHERE sessionId = :sessionId AND state IN (:states)")
    abstract suspend fun countItemsInStates(sessionId: String, states: List<String>): Int

    @Query("SELECT COUNT(*) FROM queue_items WHERE sessionId = :sessionId")
    abstract suspend fun countItems(sessionId: String): Int

    @Query(
        "SELECT a.* FROM attempts a INNER JOIN queue_items q ON q.id = a.queueItemId " +
            "WHERE q.sessionId = :sessionId ORDER BY q.ordinal, a.number",
    )
    abstract suspend fun attemptsForSession(sessionId: String): List<AttemptEntity>

    @Query(
        "SELECT a.* FROM attempts a INNER JOIN queue_items q ON q.id = a.queueItemId " +
            "WHERE q.sessionId = :sessionId ORDER BY q.ordinal, a.number",
    )
    abstract fun observeAttempts(sessionId: String): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts WHERE queueItemId = :itemId ORDER BY number DESC LIMIT 1")
    abstract suspend fun latestAttempt(itemId: String): AttemptEntity?

    @Query("SELECT * FROM attempts WHERE id = :attemptId")
    abstract suspend fun attemptById(attemptId: String): AttemptEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM attempts a INNER JOIN queue_items q ON q.id = a.queueItemId WHERE q.sessionId = :sessionId AND a.id = :attemptId)")
    abstract suspend fun attemptExistsForSession(sessionId: String, attemptId: String): Boolean

    @Query(
        "SELECT a.* FROM attempts a INNER JOIN queue_items q ON q.id = a.queueItemId " +
            "WHERE q.sessionId = :sessionId AND a.outcome IS NULL LIMIT 1",
    )
    abstract suspend fun openAttemptForSession(sessionId: String): AttemptEntity?

    @Query("SELECT q.sessionId FROM attempts a INNER JOIN queue_items q ON q.id = a.queueItemId WHERE a.id = :attemptId")
    abstract suspend fun sessionIdForAttempt(attemptId: String): String?

    @Query("SELECT * FROM events WHERE sessionId = :sessionId ORDER BY seq")
    abstract suspend fun events(sessionId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE sessionId = :sessionId ORDER BY seq")
    abstract fun observeEvents(sessionId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE attemptId = :attemptId ORDER BY seq")
    abstract suspend fun eventsForAttempt(attemptId: String): List<EventEntity>

    @Query("SELECT * FROM telemetry WHERE sessionId = :sessionId ORDER BY wallTimeMs, id")
    abstract suspend fun telemetry(sessionId: String): List<TelemetryEntity>

    @Query("SELECT * FROM telemetry WHERE sessionId = :sessionId ORDER BY wallTimeMs, id")
    abstract fun observeTelemetry(sessionId: String): Flow<List<TelemetryEntity>>

    @Query("UPDATE sessions SET lastSeq = lastSeq + 1 WHERE id = :sessionId")
    abstract suspend fun incrementSeq(sessionId: String): Int

    @Query("SELECT lastSeq FROM sessions WHERE id = :sessionId")
    abstract suspend fun lastSeq(sessionId: String): Long?

    @Query("UPDATE sessions SET state = :newState WHERE id = :sessionId AND state = :expectedState")
    abstract suspend fun updateSessionState(sessionId: String, expectedState: String, newState: String): Int

    @Query("UPDATE sessions SET state = :newState WHERE id = :sessionId AND state IN (:expectedStates)")
    abstract suspend fun updateSessionStateFrom(sessionId: String, expectedStates: List<String>, newState: String): Int

    @Query("UPDATE sessions SET startedWallTimeMs = :wallTimeMs, activeStartedElapsedMs = :elapsedMs, activeStartedBootId = :bootId WHERE id = :sessionId AND startedWallTimeMs IS NULL")
    abstract suspend fun markSessionStarted(sessionId: String, wallTimeMs: Long, elapsedMs: Long, bootId: String): Int

    @Query("UPDATE sessions SET activeStartedElapsedMs = :elapsedMs, activeStartedBootId = :bootId WHERE id = :sessionId AND activeStartedElapsedMs IS NULL")
    abstract suspend fun ensureActiveStarted(sessionId: String, elapsedMs: Long, bootId: String): Int

    @Query("UPDATE sessions SET persistedActiveMs = :activeMs, activeStartedElapsedMs = :startedElapsedMs, activeStartedBootId = :bootId WHERE id = :sessionId")
    abstract suspend fun updateActiveAccounting(
        sessionId: String,
        activeMs: Long,
        startedElapsedMs: Long?,
        bootId: String?,
    ): Int

    @Query("UPDATE sessions SET ownerInstanceId = :ownerInstanceId WHERE id = :sessionId")
    abstract suspend fun updateOwner(sessionId: String, ownerInstanceId: String?): Int

    @Query("UPDATE sessions SET finishedWallTimeMs = :wallTimeMs, persistedActiveMs = :activeMs, activeStartedElapsedMs = NULL, activeStartedBootId = NULL, ownerInstanceId = NULL WHERE id = :sessionId")
    abstract suspend fun markSessionFinished(sessionId: String, wallTimeMs: Long, activeMs: Long): Int

    @Query("UPDATE sessions SET failureCode = :code, failureDomain = :domain, failureDetail = :detail WHERE id = :sessionId")
    abstract suspend fun recordSessionFailure(sessionId: String, code: String, domain: String, detail: String): Int

    @Query("UPDATE queue_items SET state = :newState WHERE id = :itemId AND state IN (:expectedStates)")
    abstract suspend fun updateQueueItemState(
        itemId: String,
        expectedStates: List<String>,
        newState: String,
    ): Int

    @Query("UPDATE attempts SET stage = :stage WHERE id = :attemptId AND outcome IS NULL")
    abstract suspend fun updateAttemptStage(attemptId: String, stage: String): Int

    @Query(
        "UPDATE attempts SET outcome = :outcome, validity = :validity, failureCode = :failureCode, " +
            "failureDomain = :failureDomain, failureConfidence = :failureConfidence, " +
            "failureDetail = :failureDetail, finishedWallTimeMs = :finishedWallTimeMs, " +
            "elapsedMs = :elapsedMs " +
            "WHERE id = :attemptId AND outcome IS NULL",
    )
    abstract suspend fun finishAttempt(
        attemptId: String,
        outcome: String,
        validity: String,
        failureCode: String?,
        failureDomain: String?,
        failureConfidence: String?,
        failureDetail: String?,
        finishedWallTimeMs: Long,
        elapsedMs: Long?,
    ): Int

    @Query("SELECT * FROM runner_slot WHERE singletonId = 1")
    abstract suspend fun runnerSlot(): RunnerSlotEntity?

    @Query("UPDATE runner_slot SET sessionId = :sessionId, ownerInstanceId = :ownerInstanceId WHERE singletonId = 1 AND (sessionId IS NULL OR sessionId = :sessionId)")
    abstract suspend fun claimRunnerSlot(sessionId: String, ownerInstanceId: String): Int

    @Query("UPDATE runner_slot SET sessionId = NULL, ownerInstanceId = NULL WHERE singletonId = 1 AND sessionId = :sessionId")
    abstract suspend fun releaseRunnerSlot(sessionId: String): Int

    @Query("UPDATE runner_slot SET sessionId = NULL, ownerInstanceId = NULL WHERE singletonId = 1")
    abstract suspend fun releaseRunnerSlotAlways(): Int
}
