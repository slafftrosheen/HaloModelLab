package com.slafftrosheen.halomodellab.profiles

import com.slafftrosheen.halomodellab.domain.ModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProfilesTest {
    @Test
    fun suitesCoverOnlyDeclaredTextRolesAndOnlyFixedSmokeIsRunnable() {
        assertEquals(
            setOf(ModelRole.FAST, ModelRole.CHAT, ModelRole.THINK, ModelRole.ACTION),
            TextProfileRegistry.all().map { it.role }.toSet(),
        )
        assertEquals(setOf("fast-smoke"), TextProfileRegistry.all().filter { it.runnable }.map { it.suiteId }.toSet())
        assertEquals(ProfileAvailability.SOURCE_IMPLEMENTED, TestProfileRegistry.resolve("fast-smoke", "1")?.availability)
        assertEquals(ProfileAvailability.CONTRACT_ONLY, TestProfileRegistry.resolve("action-standard", "2")?.availability)
    }

    @Test
    fun fixtureHashIsStableAndChangesWhenProbeChanges() {
        val suite = requireNotNull(TextProfileRegistry.resolve("think-standard", "1"))
        assertEquals(suite.fixtureManifestSha256(), suite.fixtureManifestSha256())
        val changed = suite.copy(cases = suite.cases.map { it.copy(prompt = it.prompt + " Answer only.") })
        assertNotEquals(suite.fixtureManifestSha256(), changed.fixtureManifestSha256())
    }

    @Test
    fun warmSeriesHasMultipleProbesButNoColdCacheClaim() {
        val warm = requireNotNull(TextProfileRegistry.resolve("fast-warm-series", "1"))
        assertEquals(TextProbeMode.WARM_SERIES, warm.mode)
        assertEquals(2, warm.cases.size)
        assertFalse(warm.canonicalJson().contains("coldFilesystemCache"))
    }

    @Test
    fun changingTheRunnableFastSmokeContractDisablesItsRunnableMarker() {
        val smoke = requireNotNull(TextProfileRegistry.resolve("fast-smoke", "1"))
        assertTrue(smoke.runnable)
        val changed = smoke.copy(cases = listOf(smoke.cases.single().copy(expectedValue = "READY!")))
        assertFalse(changed.runnable)
    }
}
