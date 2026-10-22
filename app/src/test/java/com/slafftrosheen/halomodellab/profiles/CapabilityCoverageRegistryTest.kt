package com.slafftrosheen.halomodellab.profiles

import com.slafftrosheen.halomodellab.domain.ModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityCoverageRegistryTest {
    @Test
    fun everyKnownRoleIsShownAndOnlyFixedFastSmokeIsImplemented() {
        val rows = CapabilityCoverageRegistry.all()
        assertEquals(ModelRole.entries.toSet(), rows.map { it.role }.toSet())
        assertEquals(
            setOf(ModelRole.FAST),
            rows.filter { it.status == CapabilityCoverageStatus.SOURCE_IMPLEMENTED }.map { it.role }.toSet(),
        )
        assertTrue(rows.all { it.fixtureCode.isNotBlank() })
    }

    @Test
    fun unsupportedModalitiesNameHaloOwnerAndMissingLabPath() {
        val vision = requireNotNull(CapabilityCoverageRegistry.resolve("vision-smoke"))
        assertEquals("NO_LAB_IMAGE_REQUEST_OR_FIXTURE_LEASE", vision.limitationCode)
        assertNotNull(vision.haloOwnerSurface)
        val stt = requireNotNull(CapabilityCoverageRegistry.resolve("stt-file"))
        assertEquals("NO_LAB_AUDIO_REQUEST_OR_PACK_LEASE", stt.limitationCode)
        assertNotNull(stt.haloOwnerSurface)
        val vad = requireNotNull(CapabilityCoverageRegistry.resolve("vad-segments"))
        assertEquals(CapabilityCoverageStatus.UNSUPPORTED, vad.status)
        assertEquals("NO_LAB_VAD_SEGMENT_PATH", vad.limitationCode)
    }
}
