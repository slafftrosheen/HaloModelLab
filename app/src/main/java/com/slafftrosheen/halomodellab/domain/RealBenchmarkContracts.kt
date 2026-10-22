package com.slafftrosheen.halomodellab.domain

import java.security.MessageDigest

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/** Independent versions for persistence, export, suites, policy, roles, and the bridge. */
object RealContractVersions {
    const val PLAN_SCHEMA: Int = 1
    const val EXPORT_SCHEMA: Int = 1
    const val QUALIFICATION_POLICY: Int = 1
    const val ROLE_MAPPING: Int = 1
    const val PROTOCOL_MAJOR: Int = 1
    const val PROTOCOL_MINOR: Int = 0
}

data class StageBudgets(
    val loadMs: Long = DEFAULT_LOAD_MS,
    val executeMs: Long = DEFAULT_EXECUTE_MS,
    val unloadMs: Long = DEFAULT_UNLOAD_MS,
) {
    init {
        require(loadMs in MIN_STAGE_MS..MAX_STAGE_MS)
        require(executeMs in MIN_STAGE_MS..MAX_STAGE_MS)
        require(unloadMs in MIN_STAGE_MS..MAX_STAGE_MS)
    }

    val maximumAttemptMs: Long = Math.addExact(Math.addExact(loadMs, executeMs), unloadMs)

    companion object {
        const val DEFAULT_LOAD_MS = 300_000L
        const val DEFAULT_EXECUTE_MS = 120_000L
        const val DEFAULT_UNLOAD_MS = 30_000L
        const val MIN_STAGE_MS = 1_000L
        const val MAX_STAGE_MS = 1_800_000L
    }
}

data class ArtifactPart(
    val relativeName: String,
    val sha256: String,
    val byteCount: Long,
) {
    init {
        require(relativeName.isNotBlank() && relativeName.length <= MAX_NAME_LENGTH)
        require(!relativeName.startsWith('/') && '\\' !in relativeName)
        require(relativeName.none { it.code < 32 || it.code == 127 })
        require(relativeName.split('/').all { it.isNotBlank() && it != "." && it != ".." })
        require(':' !in relativeName)
        require(SHA256_HEX.matches(sha256))
        require(byteCount >= 0L)
    }

    companion object {
        const val MAX_NAME_LENGTH = 240
    }
}

data class ArtifactManifest(
    val providerId: String,
    val modelId: String,
    val generationId: String,
    val parts: List<ArtifactPart>,
) {
    init {
        require(isBoundedIdentifier(providerId, MAX_ID_LENGTH))
        require(isBoundedIdentifier(modelId, MAX_ID_LENGTH))
        require(isBoundedIdentifier(generationId, MAX_ID_LENGTH))
        require(parts.isNotEmpty() && parts.size <= MAX_ARTIFACT_PARTS)
        require(parts.map { it.relativeName }.distinct().size == parts.size)
    }

    /** Hashes a documented UTF-8 canonical JSON encoding, never Kotlin toString() or map order. */
    fun canonicalSha256(): String = sha256(canonicalJson().toByteArray(Charsets.UTF_8))

    fun canonicalJson(): String = buildString {
        append('{')
        jsonField("providerId", providerId)
        append(',')
        jsonField("modelId", modelId)
        append(',')
        jsonField("generationId", generationId)
        append(',').append("\"parts\":[")
        parts.sortedBy { it.relativeName }.forEachIndexed { index, part ->
            if (index != 0) append(',')
            append('{')
            jsonField("relativeName", part.relativeName)
            append(',')
            jsonField("sha256", part.sha256)
            append(',').append("\"byteCount\":").append(part.byteCount).append('}')
        }
        append("]}")
    }

    companion object {
        const val MAX_ID_LENGTH = 160
        const val MAX_ARTIFACT_PARTS = 256
    }
}

enum class RealBackend { AUTO, CPU, GPU, NPU, UNSPECIFIED }

sealed interface BackendWireValue {
    data class Supported(val value: RealBackend) : BackendWireValue
    data class Unsupported(val wireValue: String) : BackendWireValue

    companion object {
        fun decode(raw: String): BackendWireValue = when (raw) {
            "AUTO" -> Supported(RealBackend.AUTO)
            "CPU" -> Supported(RealBackend.CPU)
            "GPU" -> Supported(RealBackend.GPU)
            "NPU" -> Supported(RealBackend.NPU)
            "UNSPECIFIED" -> Supported(RealBackend.UNSPECIFIED)
            else -> Unsupported(raw.take(MAX_UNKNOWN_VALUE_LENGTH))
        }
    }
}

/**
 * Stable role mapping observed at Halo commit 2d7b650. Speech and embedding roles do not
 * appear in Halo's generic AiModelRole enum and deliberately remain unsupported here.
 */
sealed interface RoleWireMapping {
    data class Supported(val labRole: ModelRole, val haloRole: String) : RoleWireMapping
    data class Unsupported(val wireValue: String) : RoleWireMapping

    companion object {
        fun encode(role: ModelRole): RoleWireMapping = when (role) {
            ModelRole.CHAT -> Supported(role, "CHAT")
            ModelRole.FAST -> Supported(role, "FAST")
            ModelRole.THINK -> Supported(role, "REASONING")
            ModelRole.ACTION -> Supported(role, "AGENTIC")
            ModelRole.VISION -> Supported(role, "VISION")
            ModelRole.STT, ModelRole.TTS, ModelRole.EMBEDDING -> Unsupported(role.name)
        }

        fun decode(raw: String): RoleWireMapping = when (raw) {
            "CHAT" -> Supported(ModelRole.CHAT, raw)
            "FAST" -> Supported(ModelRole.FAST, raw)
            "REASONING" -> Supported(ModelRole.THINK, raw)
            "AGENTIC" -> Supported(ModelRole.ACTION, raw)
            "VISION" -> Supported(ModelRole.VISION, raw)
            else -> Unsupported(raw.take(MAX_UNKNOWN_VALUE_LENGTH))
        }
    }
}

data class RuntimeCapability(
    val role: ModelRole,
    val backends: Set<RealBackend>,
    val supportsStreaming: Boolean,
    val supportsToolCalls: Boolean,
    val maximumInputTokens: Int?,
    val maximumOutputTokens: Int?,
) {
    init {
        require(backends.isNotEmpty())
        require(maximumInputTokens == null || maximumInputTokens > 0)
        require(maximumOutputTokens == null || maximumOutputTokens > 0)
        require(RoleWireMapping.encode(role) is RoleWireMapping.Supported)
    }
}

data class RuntimeCapabilities(
    val capturedAtWallTimeMs: Long,
    val haloSourceId: String,
    val runtimeId: String,
    val runtimeVersion: String,
    val adapterCompatibilityId: String,
    val capabilities: List<RuntimeCapability>,
    val snapshotSha256: String,
) {
    init {
        require(capturedAtWallTimeMs > 0L)
        require(listOf(haloSourceId, runtimeId, runtimeVersion, adapterCompatibilityId)
            .all { isBoundedIdentifier(it, MAX_CAPABILITY_ID_LENGTH) })
        require(capabilities.isNotEmpty())
        require(SHA256_HEX.matches(snapshotSha256))
        require(capabilities.map { it.role }.distinct().size == capabilities.size)
    }

    fun canonicalJson(): String = buildString {
        append('{')
        append("\"capturedAtWallTimeMs\":").append(capturedAtWallTimeMs).append(',')
        jsonField("haloSourceId", haloSourceId)
        append(',')
        jsonField("runtimeId", runtimeId)
        append(',')
        jsonField("runtimeVersion", runtimeVersion)
        append(',')
        jsonField("adapterCompatibilityId", adapterCompatibilityId)
        append(',').append("\"capabilities\":[")
        capabilities.sortedBy { it.role.name }.forEachIndexed { index, capability ->
            if (index != 0) append(',')
            append('{')
            jsonField("role", capability.role.name)
            append(',').append("\"backends\":[")
            capability.backends.map { it.name }.sorted().forEachIndexed { backendIndex, backend ->
                if (backendIndex != 0) append(',')
                append(jsonString(backend))
            }
            append("],\"supportsStreaming\":").append(capability.supportsStreaming)
            append(",\"supportsToolCalls\":").append(capability.supportsToolCalls)
            append(",\"maximumInputTokens\":").append(capability.maximumInputTokens ?: "null")
            append(",\"maximumOutputTokens\":").append(capability.maximumOutputTokens ?: "null")
            append('}')
        }
        append("]}")
    }

    fun canonicalSha256(): String = sha256(canonicalJson().toByteArray(Charsets.UTF_8))
}

data class ExecutionScope(
    val schemaVersion: Int = 1,
    val deviceProfileId: String,
    val osBuildId: String,
    val haloSourceId: String,
    val labSourceId: String,
    val runtimeId: String,
    val runtimeVersion: String,
    val adapterCompatibilityId: String,
    val artifactManifestSha256: String,
    val role: ModelRole,
    val requestedBackend: RealBackend,
    val effectiveBackend: RealBackend?,
    val effectiveConfigurationSha256: String,
    val suiteId: String,
    val suiteVersion: String,
    val fixtureManifestSha256: String?,
    val qualificationPolicyVersion: Int,
    val qualificationPolicySha256: String,
    val measurementMode: String,
    val language: String?,
    val voiceId: String?,
    val sourceIdentitiesVerified: Boolean,
) {
    init {
        require(schemaVersion == 1)
        require(listOf(
            deviceProfileId, osBuildId, haloSourceId, labSourceId, runtimeId,
            runtimeVersion, adapterCompatibilityId, suiteId, suiteVersion, measurementMode,
        ).all { isBoundedIdentifier(it, MAX_SCOPE_FIELD_LENGTH) })
        require(isUuid(deviceProfileId))
        require(qualificationPolicyVersion > 0)
        require(listOf(artifactManifestSha256, effectiveConfigurationSha256, qualificationPolicySha256)
            .all(SHA256_HEX::matches))
        require(fixtureManifestSha256 == null || SHA256_HEX.matches(fixtureManifestSha256))
        require(language == null || language.length <= MAX_OPTION_LENGTH)
        require(voiceId == null || voiceId.length <= MAX_OPTION_LENGTH)
        require(!sourceIdentitiesVerified || listOf(haloSourceId, labSourceId).none(::isUnverifiedSourceId))
    }

    fun mayReuseExactDeviceEvidence(): Boolean =
        sourceIdentitiesVerified &&
            fixtureManifestSha256 != null &&
            effectiveBackend != null &&
                effectiveBackend != RealBackend.UNSPECIFIED &&
                effectiveBackend != RealBackend.AUTO

    companion object {
        const val MAX_OPTION_LENGTH = 128
        const val MAX_SCOPE_FIELD_LENGTH = 256
    }
}

data class ConfigurationValue(val key: String, val value: String) {
    init {
        require(CONFIG_KEY.matches(key))
        require(value.length <= MAX_CONFIGURATION_VALUE_LENGTH)
    }

    companion object {
        private val CONFIG_KEY = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
        const val MAX_CONFIGURATION_VALUE_LENGTH = 512
    }
}

data class RealCasePlan(
    val queueItemId: String,
    val providerId: String,
    val modelId: String,
    val artifactGenerationId: String,
    val artifactManifest: ArtifactManifest?,
    val artifactUnavailableReason: String?,
    val role: ModelRole,
    val requestedBackend: RealBackend,
    val requestedConfiguration: List<ConfigurationValue>,
    val suiteId: String,
    val suiteVersion: String,
    val caseId: String,
    val fixtureManifestSha256: String?,
    val repetitionIndex: Int,
    val maximumOutputTokens: Int?,
) {
    init {
        require(isUuid(queueItemId))
        require(isBoundedIdentifier(providerId, ArtifactManifest.MAX_ID_LENGTH))
        require(isBoundedIdentifier(modelId, ArtifactManifest.MAX_ID_LENGTH))
        require(isBoundedIdentifier(artifactGenerationId, ArtifactManifest.MAX_ID_LENGTH))
        require((artifactManifest == null) == !artifactUnavailableReason.isNullOrBlank())
        require(artifactManifest == null || (artifactManifest.providerId == providerId &&
            artifactManifest.modelId == modelId && artifactManifest.generationId == artifactGenerationId))
        require(RoleWireMapping.encode(role) is RoleWireMapping.Supported)
        require(isBoundedIdentifier(suiteId, MAX_CASE_IDENTIFIER_LENGTH))
        require(isBoundedIdentifier(suiteVersion, MAX_CASE_IDENTIFIER_LENGTH))
        require(isBoundedIdentifier(caseId, MAX_CASE_IDENTIFIER_LENGTH))
        require(fixtureManifestSha256 == null || SHA256_HEX.matches(fixtureManifestSha256))
        require(repetitionIndex >= 0)
        require(maximumOutputTokens == null || maximumOutputTokens in 1..MAX_OUTPUT_TOKENS)
        require(requestedConfiguration.size <= MAX_CONFIGURATION_ITEMS)
        require(requestedConfiguration.map { it.key }.distinct().size == requestedConfiguration.size)
    }

    fun requestedConfigurationJson(): String = buildString {
        append('{')
        requestedConfiguration.sortedBy { it.key }.forEachIndexed { index, config ->
            if (index > 0) append(',')
            append(jsonString(config.key)).append(':').append(jsonString(config.value))
        }
        append('}')
    }

    companion object {
        const val MAX_CONFIGURATION_ITEMS = 64
        const val MAX_CASE_IDENTIFIER_LENGTH = 128
        const val MAX_OUTPUT_TOKENS = 100_000
    }
}

data class RealRunPlan(
    val sessionId: String,
    val profileId: String,
    val suiteId: String,
    val suiteVersion: String,
    val origin: EvidenceOrigin = EvidenceOrigin.DEVICE_MEASUREMENT,
    val planSchemaVersion: Int = RealContractVersions.PLAN_SCHEMA,
    val exportSchemaVersion: Int = RealContractVersions.EXPORT_SCHEMA,
    val fixtureManifestSha256: String?,
    val qualificationPolicyVersion: Int = RealContractVersions.QUALIFICATION_POLICY,
    val qualificationPolicySha256: String,
    val protocolMajor: Int = RealContractVersions.PROTOCOL_MAJOR,
    val protocolMinor: Int = RealContractVersions.PROTOCOL_MINOR,
    val stageBudgets: StageBudgets,
    val sessionBudgetMs: Long,
    val requireCharging: Boolean,
    val capabilitySnapshot: RuntimeCapabilities?,
    val cases: List<RealCasePlan>,
) {
    init {
        require(isUuid(sessionId))
        require(isBoundedIdentifier(profileId, RealCasePlan.MAX_CASE_IDENTIFIER_LENGTH))
        require(isBoundedIdentifier(suiteId, RealCasePlan.MAX_CASE_IDENTIFIER_LENGTH))
        require(isBoundedIdentifier(suiteVersion, RealCasePlan.MAX_CASE_IDENTIFIER_LENGTH))
        require(origin == EvidenceOrigin.DEVICE_MEASUREMENT)
        require(planSchemaVersion == RealContractVersions.PLAN_SCHEMA)
        require(exportSchemaVersion > 0 && qualificationPolicyVersion > 0)
        require(fixtureManifestSha256 == null || SHA256_HEX.matches(fixtureManifestSha256))
        require(SHA256_HEX.matches(qualificationPolicySha256))
        require(protocolMajor > 0 && protocolMinor >= 0)
        require(sessionBudgetMs in MIN_SESSION_BUDGET_MS..MAX_SESSION_BUDGET_MS)
        require(cases.isNotEmpty() && cases.size <= MAX_CASES)
        require(cases.map { it.queueItemId }.distinct().size == cases.size)
        require(cases.all { it.suiteId == suiteId && it.suiteVersion == suiteVersion })
        require(cases.groupBy { Triple(it.providerId, it.modelId, it.artifactGenerationId) }
            .values.all { group ->
                group.map { it.artifactManifest?.canonicalSha256() to it.artifactUnavailableReason }
                    .distinct().size == 1
            })
        require(capabilitySnapshot == null || capabilitySnapshot.canonicalSha256() == capabilitySnapshot.snapshotSha256)
    }

    /** Full immutable input snapshot suitable for the existing sessions.planJson column. */
    fun canonicalJson(): String = buildString {
        append('{')
        append("\"planSchemaVersion\":").append(planSchemaVersion).append(',')
        append("\"exportSchemaVersion\":").append(exportSchemaVersion).append(',')
        jsonField("sessionId", sessionId)
        append(',')
        jsonField("origin", origin.name)
        append(',')
        jsonField("profileId", profileId)
        append(',')
        jsonField("suiteId", suiteId)
        append(',')
        jsonField("suiteVersion", suiteVersion)
        append(',')
        append("\"fixtureManifestSha256\":").append(fixtureManifestSha256?.let(::jsonString) ?: "null")
        append(',')
        append("\"qualificationPolicyVersion\":").append(qualificationPolicyVersion).append(',')
        jsonField("qualificationPolicySha256", qualificationPolicySha256)
        append(',')
        append("\"protocolMajor\":").append(protocolMajor).append(',')
        append("\"protocolMinor\":").append(protocolMinor).append(',')
        append("\"stageBudgets\":{\"loadMs\":").append(stageBudgets.loadMs)
        append(",\"executeMs\":").append(stageBudgets.executeMs)
        append(",\"unloadMs\":").append(stageBudgets.unloadMs).append("},")
        append("\"sessionBudgetMs\":").append(sessionBudgetMs).append(',')
        append("\"requireCharging\":").append(requireCharging).append(',')
        append("\"capabilitySnapshot\":")
        append(capabilitySnapshot?.let { jsonString(it.snapshotSha256) } ?: "null")
        append(',').append("\"cases\":[")
        cases.forEachIndexed { index, case ->
            if (index > 0) append(',')
            append('{')
            jsonField("queueItemId", case.queueItemId)
            append(',')
            jsonField("providerId", case.providerId)
            append(',')
            jsonField("modelId", case.modelId)
            append(',')
            jsonField("artifactGenerationId", case.artifactGenerationId)
            append(',')
            append("\"artifactManifest\":")
            append(case.artifactManifest?.canonicalJson() ?: "null")
            append(',')
            append("\"artifactUnavailableReason\":")
            append(case.artifactUnavailableReason?.let(::jsonString) ?: "null")
            append(',')
            jsonField("role", case.role.name)
            append(',')
            jsonField("haloRole", (RoleWireMapping.encode(case.role) as RoleWireMapping.Supported).haloRole)
            append(',')
            jsonField("requestedBackend", case.requestedBackend.name)
            append(',')
            append("\"requestedConfiguration\":{")
            case.requestedConfiguration.sortedBy { it.key }.forEachIndexed { configIndex, value ->
                if (configIndex > 0) append(',')
                append(jsonString(value.key)).append(':').append(jsonString(value.value))
            }
            append("},")
            jsonField("suiteId", case.suiteId)
            append(',')
            jsonField("suiteVersion", case.suiteVersion)
            append(',')
            jsonField("caseId", case.caseId)
            append(',')
            append("\"fixtureManifestSha256\":")
                .append(case.fixtureManifestSha256?.let(::jsonString) ?: "null")
            append(',').append("\"repetitionIndex\":").append(case.repetitionIndex).append(',')
            append("\"maximumOutputTokens\":").append(case.maximumOutputTokens ?: "null").append('}')
        }
        append("]}")
    }

    fun digestSha256(): String = sha256(canonicalJson().toByteArray(Charsets.UTF_8))

    companion object {
        const val MIN_SESSION_BUDGET_MS = 60_000L
        const val MAX_SESSION_BUDGET_MS = 86_400_000L
        const val MAX_CASES = 500
    }
}

data class RemoteEventKey(
    val serverEpoch: String,
    val remoteRunId: String,
    val sequence: Long,
) {
    init {
        require(isBoundedIdentifier(serverEpoch, MAX_REMOTE_ID_LENGTH))
        require(isBoundedIdentifier(remoteRunId, MAX_REMOTE_ID_LENGTH))
        require(sequence > 0L)
    }
}

enum class StopDisposition {
    REQUEST_ACCEPTED,
    CLEANUP_CONFIRMED,
    WORKER_DEATH_CONFIRMED,
    ALREADY_TERMINAL,
    TERMINATION_UNCONFIRMED,
}

data class StopReceipt(
    val attemptId: String,
    val workerInstanceId: String?,
    val disposition: StopDisposition,
    val evidenceSequence: Long?,
) {
    init {
        require(attemptId.isNotBlank())
        require(workerInstanceId == null || isBoundedIdentifier(workerInstanceId, MAX_REMOTE_ID_LENGTH))
        require(evidenceSequence == null || evidenceSequence > 0L)
    }

    val safeToReleaseExecutionLease: Boolean
        get() = disposition == StopDisposition.CLEANUP_CONFIRMED ||
            disposition == StopDisposition.WORKER_DEATH_CONFIRMED
}

private const val MAX_UNKNOWN_VALUE_LENGTH = 128

private fun StringBuilder.jsonField(name: String, value: String) {
    append(jsonString(name)).append(':').append(jsonString(value))
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) {
                append("\\u").append(char.code.toString(16).padStart(4, '0'))
            } else append(char)
        }
    }
    append('"')
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun isUuid(value: String): Boolean = UUID_PATTERN.matches(value)

private fun isBoundedIdentifier(value: String, maximumLength: Int): Boolean =
    value.isNotBlank() && value.length <= maximumLength && value.none { it.code < 32 || it.code == 127 }

private fun isUnverifiedSourceId(value: String): Boolean =
    value.equals("unknown", ignoreCase = true) ||
        value.equals("not_recorded", ignoreCase = true) ||
        value.endsWith("+dirty", ignoreCase = true)

private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
private const val MAX_CAPABILITY_ID_LENGTH = 256
private const val MAX_REMOTE_ID_LENGTH = 256
