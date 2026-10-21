package com.slafftrosheen.halomodellab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The single source of truth.
 *
 * Version 1, exported schema, no destructive migration. Journal mode is WAL: exports read a
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class LabDatabase : RoomDatabase() {

    abstract fun labDao(): LabDao

    companion object {
        const val FILE_NAME: String = "halo-model-lab.db"

        fun build(context: Context): LabDatabase =
            Room.databaseBuilder(context.applicationContext, LabDatabase::class.java, FILE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
