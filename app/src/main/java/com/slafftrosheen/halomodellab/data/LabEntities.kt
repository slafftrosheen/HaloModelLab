package com.slafftrosheen.halomodellab.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities store enum names as strings so the schema stays stable and explicit.
 * Domain types are never annotated with Room or Android annotations.
 */
@Entity(
    tableName = "sessions",
    indices = [Index("state"), Index("createdAtWallTimeMs")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val state: String,
    val planJson: String,
    val origin: String,
    val profileId: String,
    val engineId: String,
    val caseTimeoutMs: Long,
    val budgetMs: Long,
    val repeatCount: Int,
    val requireCharging: Boolean,
    val createdAtWallTimeMs: Long,
    val startedWallTimeMs: Long?,
    val finishedWallTimeMs: Long?,
    val persistedActiveMs: Long,
    val activeStartedElapsedMs: Long?,
    val lastSeq: Long,
    val failureCode: String?,
    val failureDomain: String?,
    val failureDetail: String?,
    val ownerInstanceId: String?,
)

@Entity(
    tableName = "queue_items",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "ordinal"], unique = true),
    ],
)
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val ordinal: Int,
    val subjectId: String,
    val scenarioId: String?,
    val role: String,
    val requestedBackend: String,
    val state: String,
    val repeatIndex: Int,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = QueueItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["queueItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("queueItemId"),
        Index(value = ["queueItemId", "number"], unique = true),
        Index("outcome"),
    ],
)
data class AttemptEntity(
    @PrimaryKey val id: String,
    val queueItemId: String,
    val number: Int,
    val ownerInstanceId: String,
    val stage: String,
    val outcome: String?,
    val validity: String?,
    val failureCode: String?,
    val failureDomain: String?,
    val failureConfidence: String?,
    val failureDetail: String?,
    val deadlineReason: String?,
    val startedWallTimeMs: Long,
    val startedElapsedMs: Long?,
    val finishedWallTimeMs: Long?,
    val elapsedMs: Long?,
)

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "seq"], unique = true),
        Index("attemptId"),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val seq: Long,
    val attemptId: String?,
    val stage: String?,
    val code: String,
    val boundedPayloadJson: String?,
    val wallTimeMs: Long,
    val elapsedTimeMs: Long?,
    val ownerInstanceId: String,
)

@Entity(
    tableName = "telemetry",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("attemptId")],
)
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val attemptId: String?,
    val name: String,
    val value: Double?,
    val unit: String,
    val scope: String,
    val origin: String,
    val unavailableReason: String?,
    val wallTimeMs: Long,
)

/**
 * Single-row table that owns execution. Exactly one session may hold the slot, and a session
 * cannot be claimed by two processes.
 */
@Entity(tableName = "runner_slot")
data class RunnerSlotEntity(
    @PrimaryKey val singletonId: Int,
    val sessionId: String?,
    val ownerInstanceId: String?,
) {
    companion object {
        const val SINGLETON_ID: Int = 1
        fun empty(): RunnerSlotEntity = RunnerSlotEntity(
            singletonId = SINGLETON_ID,
            sessionId = null,
            ownerInstanceId = null,
        )
    }
}

/** Projection used by the history list, so it never needs a query per row. */
data class SessionSummaryRow(
    val id: String,
    val state: String,
    val origin: String,
    val engineId: String,
    val profileId: String,
    val createdAtWallTimeMs: Long,
    val finishedWallTimeMs: Long?,
    val itemCount: Int,
    val completedCount: Int,
)
