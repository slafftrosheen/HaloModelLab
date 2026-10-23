package com.slafftrosheen.halomodellab.export

import java.security.MessageDigest

/** Validates bounded bytes from Halo against the already hash-verified replay event. */
internal object EvidenceArtifactTransfer {
    const val MAX_BYTES = 2048

    fun verifiedText(bytes: ByteArray, expectedBytes: ByteArray, expectedSha256: String): String? {
        if (bytes.size > MAX_BYTES || !bytes.contentEquals(expectedBytes) || sha256(bytes) != expectedSha256) return null
        val text = bytes.toString(Charsets.UTF_8)
        return text.takeIf { it.toByteArray(Charsets.UTF_8).contentEquals(bytes) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
