package com.slafftrosheen.halomodellab.runner

import com.slafftrosheen.halomodellab.data.SessionRepository
import com.slafftrosheen.halomodellab.domain.ArtifactManifest
import com.slafftrosheen.halomodellab.domain.ArtifactPart
import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.ConfigurationValue
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.ExecutionScope
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.RealBackend
import com.slafftrosheen.halomodellab.domain.RealCasePlan
import com.slafftrosheen.halomodellab.domain.RealContractVersions
import com.slafftrosheen.halomodellab.domain.StageBudgets
import com.slafftrosheen.halomodellab.bridge.SubmitCaseRequest
import com.slafftrosheen.halomodellab.catalog.FastSmokePlanFactory
import java.security.MessageDigest
import org.json.JSONObject

/** Rebuilds an execution request only from the immutable Room plan and its active attempt. */
class PersistedRealExecutionContextSource(
    private val repository: SessionRepository,
) : RealExecutionContextSource {
    override suspend fun forAttempt(request: BenchmarkRequest): SubmitCaseRequest? {
        val snapshot = repository.exportSnapshot(request.sessionId) ?: return null
        val detail = snapshot.detail
        if (detail.summary.origin != EvidenceOrigin.DEVICE_MEASUREMENT ||
            detail.summary.engineId != REAL_ENGINE_ID
        ) return null
        val root = runCatching { JSONObject(detail.planJson) }.getOrNull() ?: return null
        if (root.optString("sessionId") != request.sessionId ||
            root.optString("origin") != EvidenceOrigin.DEVICE_MEASUREMENT.name ||
            root.optString("suiteId") != FastSmokePlanFactory.SUITE_ID ||
            root.optString("suiteVersion") != FastSmokePlanFactory.SUITE_VERSION ||
            root.optInt("protocolMajor", -1) != RealContractVersions.PROTOCOL_MAJOR
        ) return null
        val cases = root.optJSONArray("cases") ?: return null
        if (cases.length() != 1) return null
        val row = cases.optJSONObject(0) ?: return null
        val case = runCatching { parseCase(row) }.getOrNull() ?: return null
        if (case.caseId != request.caseId ||
            case.role != request.role ||
            case.requestedBackend.name != request.requestedBackend.name ||
            request.subjectId != "${case.providerId}/${case.modelId}/${case.artifactGenerationId}"
        ) return null
        if (case.providerId != LOCAL_PROVIDER_ID ||
            case.suiteId != FastSmokePlanFactory.SUITE_ID ||
            case.suiteVersion != FastSmokePlanFactory.SUITE_VERSION ||
            case.caseId != FastSmokePlanFactory.CASE_ID ||
            case.role != ModelRole.FAST || case.requestedBackend != RealBackend.CPU ||
            case.maximumOutputTokens != FastSmokePlanFactory.MAX_OUTPUT_TOKENS ||
            case.artifactUnavailableReason != null
        ) return null
        val queueItem = detail.queue.singleOrNull { it.queueItemId == case.queueItemId } ?: return null
        if (queueItem.scenarioId != request.caseId ||
            queueItem.attempts.none { it.attemptId == request.attemptId }
        ) return null

        val manifest = case.artifactManifest ?: return null
        val scope = case.executionScope ?: return null
        val evidence = snapshot.realEvidence ?: return null
        val savedPlan = evidence.plan ?: return null
        val digest = sha256(detail.planJson)
        if (savedPlan.planDigestSha256 != digest) return null
        val savedConfiguration = evidence.configurations.singleOrNull {
            it.queueItemId == case.queueItemId
        } ?: return null
        val scopeJson = scope.canonicalJson()
        if (savedConfiguration.executionScopeJson != scopeJson ||
            savedConfiguration.scopeDigestSha256 != sha256(scopeJson)
        ) return null
        val stageRow = root.optJSONObject("stageBudgets") ?: return null
        val stageBudgets = runCatching {
            StageBudgets(
                loadMs = stageRow.getLong("loadMs"),
                executeMs = stageRow.getLong("executeMs"),
                unloadMs = stageRow.getLong("unloadMs"),
            )
        }.getOrNull() ?: return null
        if (stageBudgets.maximumAttemptMs != request.caseTimeoutMs ||
            manifest.canonicalSha256() != scope.artifactManifestSha256 ||
            scope.role != case.role || scope.requestedBackend != case.requestedBackend
        ) return null

        return runCatching {
            SubmitCaseRequest(
                sessionId = request.sessionId,
                attemptId = request.attemptId,
                queueItemId = case.queueItemId,
                planDigestSha256 = digest,
                origin = EvidenceOrigin.DEVICE_MEASUREMENT,
                casePlan = case,
                executionScope = scope,
                stageBudgets = stageBudgets,
            )
        }.getOrNull()
    }

    private fun parseCase(row: JSONObject): RealCasePlan {
        val manifest = row.optJSONObject("artifactManifest")?.let(::parseManifest)
        val scope = row.optJSONObject("executionScope")?.let(::parseScope)
        val configuration = row.optJSONObject("requestedConfiguration")?.let { config ->
            config.keys().asSequence().map { key -> ConfigurationValue(key, config.getString(key)) }.toList()
        }.orEmpty()
        return RealCasePlan(
            queueItemId = row.getString("queueItemId"),
            providerId = row.getString("providerId"),
            modelId = row.getString("modelId"),
            artifactGenerationId = row.getString("artifactGenerationId"),
            artifactManifest = manifest,
            artifactUnavailableReason = row.optNullableString("artifactUnavailableReason"),
            role = ModelRole.valueOf(row.getString("role")),
            requestedBackend = RealBackend.valueOf(row.getString("requestedBackend")),
            requestedConfiguration = configuration,
            suiteId = row.getString("suiteId"),
            suiteVersion = row.getString("suiteVersion"),
            caseId = row.getString("caseId"),
            fixtureManifestSha256 = row.optNullableString("fixtureManifestSha256"),
            repetitionIndex = row.getInt("repetitionIndex"),
            maximumOutputTokens = row.optNullableInt("maximumOutputTokens"),
            executionScope = scope,
        )
    }

    private fun parseManifest(row: JSONObject) = ArtifactManifest(
        providerId = row.getString("providerId"),
        modelId = row.getString("modelId"),
        generationId = row.getString("generationId"),
        parts = row.getJSONArray("parts").let { parts ->
            (0 until parts.length()).map { index -> parts.getJSONObject(index) }.map { part ->
                ArtifactPart(part.getString("relativeName"), part.getString("sha256"), part.getLong("byteCount"))
            }
        },
    )

    private fun parseScope(row: JSONObject) = ExecutionScope(
        schemaVersion = row.getInt("schemaVersion"),
        deviceProfileId = row.getString("deviceProfileId"),
        osBuildId = row.getString("osBuildId"),
        haloSourceId = row.getString("haloSourceId"),
        labSourceId = row.getString("labSourceId"),
        runtimeId = row.getString("runtimeId"),
        runtimeVersion = row.getString("runtimeVersion"),
        adapterCompatibilityId = row.getString("adapterCompatibilityId"),
        artifactManifestSha256 = row.getString("artifactManifestSha256"),
        role = ModelRole.valueOf(row.getString("role")),
        requestedBackend = RealBackend.valueOf(row.getString("requestedBackend")),
        effectiveBackend = row.optNullableString("effectiveBackend")?.let(RealBackend::valueOf),
        effectiveConfigurationSha256 = row.getString("effectiveConfigurationSha256"),
        suiteId = row.getString("suiteId"),
        suiteVersion = row.getString("suiteVersion"),
        fixtureManifestSha256 = row.optNullableString("fixtureManifestSha256"),
        qualificationPolicyVersion = row.getInt("qualificationPolicyVersion"),
        qualificationPolicySha256 = row.getString("qualificationPolicySha256"),
        measurementMode = row.getString("measurementMode"),
        language = row.optNullableString("language"),
        voiceId = row.optNullableString("voiceId"),
        sourceIdentitiesVerified = row.getBoolean("sourceIdentitiesVerified"),
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val REAL_ENGINE_ID = "halo-lab-bridge-v1"
        const val LOCAL_PROVIDER_ID = "provider.litert.local"
    }
}
