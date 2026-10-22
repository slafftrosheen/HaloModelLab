package com.slafftrosheen.halomodellab.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealPlanMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LabDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun versionOneSyntheticHistorySurvivesAdditiveRealSchemaMigration() {
        helper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.execSQL(
                """INSERT INTO sessions (
                    id, state, planJson, origin, profileId, engineId, caseTimeoutMs, budgetMs,
                    repeatCount, requireCharging, createdAtWallTimeMs, persistedActiveMs, lastSeq
                ) VALUES (
                    'legacy-session', 'COMPLETED', '{"origin":"SYNTHETIC"}', 'SYNTHETIC',
                    'DEMO_INFRASTRUCTURE_V1', 'demo-engine-v1', 10000, 300000, 1, 1, 1000, 50, 0
                )""".trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            LabDatabase.MIGRATION_1_2,
            LabDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query("SELECT origin, engineId FROM sessions WHERE id = 'legacy-session'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("SYNTHETIC", cursor.getString(0))
                assertEquals("demo-engine-v1", cursor.getString(1))
            }
            database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='remote_events'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            database.query("PRAGMA table_info(sessions)").use { cursor ->
                var foundBootColumn = false
                while (cursor.moveToNext()) if (cursor.getString(1) == "activeStartedBootId") foundBootColumn = true
                assertTrue(foundBootColumn)
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "real-plan-migration-test"
    }
}
