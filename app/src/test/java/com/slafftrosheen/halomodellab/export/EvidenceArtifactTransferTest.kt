package com.slafftrosheen.halomodellab.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest

class EvidenceArtifactTransferTest {
    @Test fun acceptsOnlyExactBoundedUtf8BytesAndHash() {
        val expected = "READY ✓".toByteArray(Charsets.UTF_8)
        assertEquals("READY ✓", EvidenceArtifactTransfer.verifiedText(expected, expected, sha256(expected)))
        assertNull(EvidenceArtifactTransfer.verifiedText("CHANGED".toByteArray(), expected, sha256(expected)))
        assertNull(EvidenceArtifactTransfer.verifiedText(expected, expected, "0".repeat(64)))
        assertNull(EvidenceArtifactTransfer.verifiedText(byteArrayOf(0xC3.toByte(), 0x28), byteArrayOf(0xC3.toByte(), 0x28), sha256(byteArrayOf(0xC3.toByte(), 0x28))))
        assertNull(EvidenceArtifactTransfer.verifiedText(ByteArray(EvidenceArtifactTransfer.MAX_BYTES + 1), ByteArray(EvidenceArtifactTransfer.MAX_BYTES + 1), sha256(ByteArray(EvidenceArtifactTransfer.MAX_BYTES + 1))))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
