package com.slafftrosheen.halomodellab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The single source of truth.
 *
 * Version 2, exported schema, no destructive migration. Journal mode is WAL: exports read a
 * consistent snapshot through queries and never copy the live database file.
 */
@Database(
    entities = [
        SessionEntity::class,
        QueueItemEntity::class,
        AttemptEntity::class,
        EventEntity::class,
        TelemetryEntity::class,
        RunnerSlotEntity::class,
        RealPlanSnapshotEntity::class,
        ModelSnapshotEntity::class,
        ConfigurationSnapshotEntity::class,
        RemoteBindingEntity::class,
        RemoteEventEntity::class,
        OutputArtifactEntity::class,
        QualificationEntity::class,
        AcquisitionProjectionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LabDatabase : RoomDatabase() {

    abstract fun labDao(): LabDao

    companion object {
        const val FILE_NAME: String = "halo-model-lab.db"

        fun build(context: Context): LabDatabase =
            Room.databaseBuilder(context.applicationContext, LabDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `real_plan_snapshots` (
                    `sessionId` TEXT NOT NULL, `planSchemaVersion` INTEGER NOT NULL,
                    `exportSchemaVersion` INTEGER NOT NULL, `suiteVersion` TEXT NOT NULL,
                    `fixtureManifestSha256` TEXT, `qualificationPolicyVersion` INTEGER NOT NULL,
                    `qualificationPolicySha256` TEXT NOT NULL, `protocolMajor` INTEGER NOT NULL,
                    `protocolMinor` INTEGER NOT NULL, `planDigestSha256` TEXT NOT NULL,
                    `capabilitySnapshotSha256` TEXT, `capabilitySnapshotJson` TEXT, PRIMARY KEY(`sessionId`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("""CREATE TABLE IF NOT EXISTS `model_snapshots` (
                    `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `providerId` TEXT NOT NULL,
                    `modelId` TEXT NOT NULL, `generationId` TEXT NOT NULL, `manifestSha256` TEXT,
                    `manifestJson` TEXT, `availabilityCode` TEXT NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_model_snapshots_sessionId_providerId_modelId_generationId` ON `model_snapshots` (`sessionId`, `providerId`, `modelId`, `generationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_model_snapshots_sessionId` ON `model_snapshots` (`sessionId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `configuration_snapshots` (
                    `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `queueItemId` TEXT NOT NULL,
                    `requestedBackend` TEXT NOT NULL, `requestedConfigurationJson` TEXT NOT NULL,
                    `effectiveBackend` TEXT, `executionScopeJson` TEXT, `scopeDigestSha256` TEXT,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`queueItemId`) REFERENCES `queue_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_configuration_snapshots_sessionId` ON `configuration_snapshots` (`sessionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_configuration_snapshots_queueItemId` ON `configuration_snapshots` (`queueItemId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `remote_bindings` (
                    `sessionId` TEXT NOT NULL, `clientRequestId` TEXT NOT NULL, `requestHashSha256` TEXT,
                    `serverEpoch` TEXT, `remoteRunId` TEXT, `currentServerInstanceId` TEXT,
                    `highestImportedSequence` INTEGER NOT NULL, PRIMARY KEY(`sessionId`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("""CREATE TABLE IF NOT EXISTS `remote_events` (
                    `serverEpoch` TEXT NOT NULL, `remoteRunId` TEXT NOT NULL, `remoteSequence` INTEGER NOT NULL,
                    `sessionId` TEXT NOT NULL, `attemptId` TEXT, `payloadHashSha256` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL, `receivedWallTimeMs` INTEGER NOT NULL,
                    PRIMARY KEY(`serverEpoch`, `remoteRunId`, `remoteSequence`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_events_sessionId` ON `remote_events` (`sessionId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `output_artifacts` (
                    `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `attemptId` TEXT,
                    `artifactKind` TEXT NOT NULL, `safeFileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL,
                    `byteCount` INTEGER, `sha256` TEXT, `availabilityCode` TEXT NOT NULL,
                    `reasonCode` TEXT, PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_output_artifacts_sessionId` ON `output_artifacts` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_output_artifacts_attemptId` ON `output_artifacts` (`attemptId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `qualifications` (
                    `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `scopeDigestSha256` TEXT NOT NULL,
                    `policyDigestSha256` TEXT NOT NULL, `status` TEXT NOT NULL, `evidenceJson` TEXT NOT NULL,
                    `updatedAtWallTimeMs` INTEGER NOT NULL, PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_qualifications_sessionId_scopeDigestSha256_policyDigestSha256` ON `qualifications` (`sessionId`, `scopeDigestSha256`, `policyDigestSha256`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `acquisition_projections` (
                    `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `generationId` TEXT,
                    `state` TEXT NOT NULL, `progressBytes` INTEGER, `expectedBytes` INTEGER,
                    `reasonCode` TEXT, `updatedAtWallTimeMs` INTEGER NOT NULL,
                    PRIMARY KEY(`providerId`, `modelId`)
                )""".trimIndent())
            }
        }
    }
}
