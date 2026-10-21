package com.slafftrosheen.halomodellab.profiles

enum class ProfileAvailability { CONTRACT_ONLY, SOURCE_IMPLEMENTED, UNSUPPORTED }

data class TestProfileDescriptor(
    val suiteId: String,
    val suiteVersion: String,
    val displayKey: String,
    val availability: ProfileAvailability,
    val unavailableReason: String?,
) {
    init {
        require(suiteId.isNotBlank() && suiteVersion.isNotBlank() && displayKey.isNotBlank())
        require((availability == ProfileAvailability.SOURCE_IMPLEMENTED) == (unavailableReason == null))
    }
}

/**
 * Stable suite identities for plans and reports. The real probes and fixture packs are owned
 * by later batches; these entries deliberately remain non-runnable until those sources exist.
 */
object TestProfileRegistry {
    private val profiles = listOf(
        TestProfileDescriptor("preflight", "1", "profile_preflight", ProfileAvailability.CONTRACT_ONLY, "PREFLIGHT_NOT_IMPLEMENTED"),
        TestProfileDescriptor("text-smoke", "1", "profile_text_smoke", ProfileAvailability.CONTRACT_ONLY, "FIXTURES_NOT_IMPLEMENTED"),
        TestProfileDescriptor("text-standard", "1", "profile_text_standard", ProfileAvailability.CONTRACT_ONLY, "FIXTURES_NOT_IMPLEMENTED"),
        TestProfileDescriptor("warm-series", "1", "profile_warm_series", ProfileAvailability.CONTRACT_ONLY, "WARM_SERIES_NOT_IMPLEMENTED"),
    )

    fun all(): List<TestProfileDescriptor> = profiles

    fun resolve(suiteId: String, suiteVersion: String): TestProfileDescriptor? =
        profiles.firstOrNull { it.suiteId == suiteId && it.suiteVersion == suiteVersion }
}
