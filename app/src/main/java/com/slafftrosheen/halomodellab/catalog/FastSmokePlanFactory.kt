package com.slafftrosheen.halomodellab.catalog

import com.slafftrosheen.halomodellab.domain.ConfigurationValue
import com.slafftrosheen.halomodellab.domain.ExecutionScope
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.RealBackend
import com.slafftrosheen.halomodellab.domain.RealCasePlan
import com.slafftrosheen.halomodellab.domain.RealRunPlan
import com.slafftrosheen.halomodellab.domain.RuntimeCapabilities
import com.slafftrosheen.halomodellab.domain.StageBudgets
import com.slafftrosheen.halomodellab.profiles.TextProfileRegistry
import java.security.MessageDigest
import java.util.UUID

sealed interface FastSmokePlanResult {
    data class Ready(val plan: RealRunPlan) : FastSmokePlanResult
    data class Unavailable(val reasonCode: String) : FastSmokePlanResult
}

/** Builds only the fixed, bounded FAST/CPU fixture from a verified installed artifact. */
object FastSmokePlanFactory {
    const val SUITE_ID = "fast-smoke"
    const val SUITE_VERSION = "1"
    const val CASE_ID = "short-ready"
    const val MAX_OUTPUT_TOKENS = 8
    const val POLICY_VERSION = 1
    const val POLICY_IDENTITY = "hml-qualification-v1:exact-scope:2-structural-failures:3-performance-samples"

    fun create(
        model: InstalledModelDescriptor,
        capabilities: RuntimeCapabilities,
        sessionId: String = UUID.randomUUID().toString(),
        deviceProfileId: String,
        osBuildId: String,
        labSourceId: String = "not_recorded",
        stageBudgets: StageBudgets = StageBudgets(),
    ): FastSmokePlanResult {
        if (!model.eligibleForArtifactLease || model.providerId != LOCAL_PROVIDER_ID) {
            return FastSmokePlanResult.Unavailable("VERIFIED_LOCAL_ARTIFACT_REQUIRED")
        }
        if (!model.mayConfigure(ModelRole.FAST) || RealBackend.CPU !in model.backendCandidates) {
            return FastSmokePlanResult.Unavailable("MODEL_FAST_CPU_UNAVAILABLE")
        }
        val runtimeCapability = capabilities.capabilities.singleOrNull { it.role == ModelRole.FAST }
            ?: return FastSmokePlanResult.Unavailable("RUNTIME_FAST_CAPABILITY_MISSING")
        if (RealBackend.CPU !in runtimeCapability.backends ||
            runtimeCapability.maximumOutputTokens?.let { it >= MAX_OUTPUT_TOKENS } != true
        ) {
            return FastSmokePlanResult.Unavailable("RUNTIME_FAST_CPU_LIMIT_UNSUPPORTED")
        }
        val manifest = model.manifest
            ?: return FastSmokePlanResult.Unavailable("ARTIFACT_MANIFEST_MISSING")
        val fixture = TextProfileRegistry.resolve(SUITE_ID, SUITE_VERSION)
            ?: return FastSmokePlanResult.Unavailable("FAST_SMOKE_FIXTURE_MISSING")
        val policySha = sha256(POLICY_IDENTITY)
        val effectiveConfigurationUnknownHash = sha256("{\"status\":\"UNKNOWN\",\"reason\":\"NOT_RECORDED\"}")
        val scope = ExecutionScope(
            deviceProfileId = deviceProfileId,
            osBuildId = osBuildId,
            haloSourceId = capabilities.haloSourceId,
            labSourceId = labSourceId,
            runtimeId = capabilities.runtimeId,
            runtimeVersion = capabilities.runtimeVersion,
            adapterCompatibilityId = capabilities.adapterCompatibilityId,
            artifactManifestSha256 = manifest.canonicalSha256(),
            role = ModelRole.FAST,
            requestedBackend = RealBackend.CPU,
            effectiveBackend = null,
            effectiveConfigurationSha256 = effectiveConfigurationUnknownHash,
            suiteId = SUITE_ID,
            suiteVersion = SUITE_VERSION,
            fixtureManifestSha256 = fixture.fixtureManifestSha256(),
            qualificationPolicyVersion = POLICY_VERSION,
            qualificationPolicySha256 = policySha,
            measurementMode = "COLD_PROCESS",
            language = "en",
            voiceId = null,
            sourceIdentitiesVerified = false,
        )
        val case = RealCasePlan(
            queueItemId = UUID.randomUUID().toString(),
            providerId = model.providerId,
            modelId = model.modelId,
            artifactGenerationId = requireNotNull(model.generationId),
            artifactManifest = manifest,
            artifactUnavailableReason = null,
            role = ModelRole.FAST,
            requestedBackend = RealBackend.CPU,
            requestedConfiguration = emptyList(),
            suiteId = SUITE_ID,
            suiteVersion = SUITE_VERSION,
            caseId = CASE_ID,
            fixtureManifestSha256 = fixture.fixtureManifestSha256(),
            repetitionIndex = 0,
            maximumOutputTokens = MAX_OUTPUT_TOKENS,
            executionScope = scope,
        )
        return FastSmokePlanResult.Ready(
            RealRunPlan(
                sessionId = sessionId,
                profileId = "halo-fast-smoke-cpu-v1",
                suiteId = SUITE_ID,
                suiteVersion = SUITE_VERSION,
                fixtureManifestSha256 = fixture.fixtureManifestSha256(),
                qualificationPolicySha256 = policySha,
                stageBudgets = stageBudgets,
                sessionBudgetMs = stageBudgets.maximumAttemptMs,
                requireCharging = true,
                capabilitySnapshot = capabilities,
                cases = listOf(case),
            ),
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private const val LOCAL_PROVIDER_ID = "provider.litert.local"
}
