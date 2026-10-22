package com.slafftrosheen.halomodellab.profiles

import com.slafftrosheen.halomodellab.domain.ModelRole

enum class CapabilityCoverageStatus { SOURCE_IMPLEMENTED, CONTRACT_ONLY, UNSUPPORTED }

data class CapabilityCoverage(
    val suiteId: String,
    val role: ModelRole?,
    val status: CapabilityCoverageStatus,
    /** Existing Halo owner/API identified by source inspection; null means none was found. */
    val haloOwnerSurface: String?,
    /** Why Halo Lab cannot run the capability over this bridge today. */
    val limitationCode: String?,
    val fixtureCode: String,
) {
    init {
        require(suiteId.matches(Regex("[A-Za-z0-9._-]{1,96}")))
        require((status == CapabilityCoverageStatus.SOURCE_IMPLEMENTED) == (limitationCode == null))
        require(haloOwnerSurface == null || haloOwnerSurface.length <= 240)
        require(fixtureCode.matches(Regex("[A-Z0-9_]{1,64}")))
    }
}

/** Explicit source coverage. Halo API presence does not imply the Lab can invoke that API. */
object CapabilityCoverageRegistry {
    private val rows = listOf(
        CapabilityCoverage("fast-smoke", ModelRole.FAST, CapabilityCoverageStatus.SOURCE_IMPLEMENTED,
            "LiteRtLocalProvider.benchmarkModel; HaloModelBenchService.startTextProbe", null, "FIXED_READY_TEXT"),
        CapabilityCoverage("chat-standard", ModelRole.CHAT, CapabilityCoverageStatus.CONTRACT_ONLY,
            "LiteRtLocalProvider.generate", "HALO_BRIDGE_FIXED_FAST_CPU_ONLY", "TEXT_ASSERTION_CONTRACTS_ONLY"),
        CapabilityCoverage("think-standard", ModelRole.THINK, CapabilityCoverageStatus.CONTRACT_ONLY,
            "LiteRtLocalProvider.generate", "HALO_BRIDGE_FIXED_FAST_CPU_ONLY", "PUBLIC_ARITHMETIC_CONTRACT_ONLY"),
        CapabilityCoverage("action-standard", ModelRole.ACTION, CapabilityCoverageStatus.CONTRACT_ONLY,
            "LiteRtLocalProvider.probeToolCalling", "HALO_BRIDGE_HAS_NO_TOOL_CALL_REQUEST", "NO_SIDE_EFFECT_FIXTURE_CONTRACT_ONLY"),
        CapabilityCoverage("vision-smoke", ModelRole.VISION, CapabilityCoverageStatus.UNSUPPORTED,
            "LiteRtLocalProvider.probeVision", "NO_LAB_IMAGE_REQUEST_OR_FIXTURE_LEASE", "NO_LAB_IMAGE_FIXTURE"),
        CapabilityCoverage("stt-file", ModelRole.STT, CapabilityCoverageStatus.UNSUPPORTED,
            "SherpaOfflineSpeechRecognitionProvider.recognize(AudioBuffer)", "NO_LAB_AUDIO_REQUEST_OR_PACK_LEASE", "NO_LICENSED_AUDIO_FIXTURE"),
        CapabilityCoverage("tts-buffer", ModelRole.TTS, CapabilityCoverageStatus.UNSUPPORTED,
            "SupertonicSpeechProvider.synthesizeToPcm; KokoroSpeechProvider.synthesizeToPcm",
            "NO_LAB_PCM_REQUEST_OR_VOICE_PACK_LEASE", "NO_PCM_FIXTURE_POLICY"),
        CapabilityCoverage("embedding-batch", ModelRole.EMBEDDING, CapabilityCoverageStatus.UNSUPPORTED,
            "LiteRtEmbeddingProvider.embed / embedMedia", "NO_LAB_VECTOR_EXECUTION_OR_METRIC_CONTRACT", "NO_PINNED_RETRIEVAL_FIXTURE"),
        CapabilityCoverage("vad-segments", null, CapabilityCoverageStatus.UNSUPPORTED,
            "ReloadableSileroVad / SherpaSpeechActivityEstimator", "NO_LAB_VAD_SEGMENT_PATH", "NO_LICENSED_SEGMENT_FIXTURE"),
    )

    fun all(): List<CapabilityCoverage> = rows
    fun resolve(suiteId: String): CapabilityCoverage? = rows.firstOrNull { it.suiteId == suiteId }
}
