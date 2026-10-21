package com.slafftrosheen.halomodellab.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Frozen real-plan versions and digest, linked to the ordinary session history row. */
@Entity(
    tableName = "real_plan_snapshots",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class RealPlanSnapshotEntity(
    @PrimaryKey
    val sessionId: String,
    val planSchemaVersion: Int,
    val exportSchemaVersion: Int,
    val suiteVersion: String,
    val fixtureManifestSha256: String?,
    val qualificationPolicyVersion: Int,
    val qualificationPolicySha256: String,
    val protocolMajor: Int,
    val protocolMinor: Int,
    val planDigestSha256: String,
    val capabilitySnapshotSha256: String?,
    val capabilitySnapshotJson: String?,
) {
    companion object { const val TABLE_NAME = "real_plan_snapshots" }
}

@Entity(
    tableName = "model_snapshots",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["sessionId", "providerId", "modelId", "generationId"], unique = true),
        Index("sessionId"),
    ],
)
data class ModelSnapshotEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val providerId: String,
    val modelId: String,
    val generationId: String,
    val manifestSha256: String?,
    val manifestJson: String?,
    val availabilityCode: String,
) {
    companion object { const val TABLE_NAME = "model_snapshots" }
}

@Entity(
    tableName = "configuration_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QueueItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["queueItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["queueItemId"], unique = true)],
)
data class ConfigurationSnapshotEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val queueItemId: String,
    val requestedBackend: String,
    val requestedConfigurationJson: String,
    val effectiveBackend: String?,
    val executionScopeJson: String?,
    val scopeDigestSha256: String?,
) {
    companion object { const val TABLE_NAME = "configuration_snapshots" }
}

@Entity(
    tableName = "remote_bindings",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class RemoteBindingEntity(
    @PrimaryKey
    val sessionId: String,
    val clientRequestId: String,
    val requestHashSha256: String?,
    val serverEpoch: String?,
    val remoteRunId: String?,
    val currentServerInstanceId: String?,
    val highestImportedSequence: Long,
) {
    companion object { const val TABLE_NAME = "remote_bindings" }
}

@Entity(
    tableName = "remote_events",
    primaryKeys = ["serverEpoch", "remoteRunId", "remoteSequence"],
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class RemoteEventEntity(
    val serverEpoch: String,
    val remoteRunId: String,
    val remoteSequence: Long,
    val sessionId: String,
    val attemptId: String?,
    val payloadHashSha256: String,
    val payloadJson: String,
    val receivedWallTimeMs: Long,
) {
    companion object { const val TABLE_NAME = "remote_events" }
}

@Entity(
    tableName = "output_artifacts",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index("attemptId")],
)
data class OutputArtifactEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val attemptId: String?,
    val artifactKind: String,
    val safeFileName: String,
    val mimeType: String,
    val byteCount: Long?,
    val sha256: String?,
    val availabilityCode: String,
    val reasonCode: String?,
) {
    companion object { const val TABLE_NAME = "output_artifacts" }
}

@Entity(
    tableName = "qualifications",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["sessionId", "scopeDigestSha256", "policyDigestSha256"], unique = true)],
)
data class QualificationEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val scopeDigestSha256: String,
    val policyDigestSha256: String,
    val status: String,
    val evidenceJson: String,
    val updatedAtWallTimeMs: Long,
) {
    companion object { const val TABLE_NAME = "qualifications" }
}

@Entity(tableName = "acquisition_projections", primaryKeys = ["providerId", "modelId"])
data class AcquisitionProjectionEntity(
    val providerId: String,
    val modelId: String,
    val generationId: String?,
    val state: String,
    val progressBytes: Long?,
    val expectedBytes: Long?,
    val reasonCode: String?,
    val updatedAtWallTimeMs: Long,
) {
    companion object { const val TABLE_NAME = "acquisition_projections" }
}
