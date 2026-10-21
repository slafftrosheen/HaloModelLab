package com.slafftrosheen.halomodellab.profiles

import com.slafftrosheen.halomodellab.domain.ModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProfilesTest {
    @Test
    fun suitesCoverOnlyDeclaredTextRolesAndRemainContractOnly() {
        assertEquals(
            setOf(ModelRole.FAST, ModelRole.CHAT, ModelRole.THINK, ModelRole.ACTION),
            TextProfileRegistry.all().map { it.role }.toSet(),
        )
        assertTrue(TextProfileRegistry.all().none { it.runnable })
        assertEquals(ProfileAvailability.CONTRACT_ONLY, TestProfileRegistry.resolve("action-standard", "1")?.availability)
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
}
