package com.slafftrosheen.halomodellab.bridge

import com.slafftrosheen.halomodellab.domain.ArtifactManifest
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.ExecutionScope
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.RealCasePlan
import com.slafftrosheen.halomodellab.domain.RealBackend
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.RemoteEventKey
import com.slafftrosheen.halomodellab.domain.RuntimeCapabilities
import com.slafftrosheen.halomodellab.domain.StageBudgets
import com.slafftrosheen.halomodellab.domain.StopReceipt
import com.slafftrosheen.halomodellab.domain.TestStage
import android.os.ParcelFileDescriptor

data class BridgeProtocolVersion(val major: Int, val minor: Int) {
    init {
        require(major > 0 && minor >= 0)
    }

    fun negotiate(server: BridgeProtocolVersion): ProtocolNegotiation =
        if (major != server.major) ProtocolNegotiation.ProtocolMismatch
        else ProtocolNegotiation.Compatible(copy(minor = minOf(minor, server.minor)))
}
sealed interface ProtocolNegotiation {
    data object ProtocolMismatch : ProtocolNegotiation
    data class Compatible(val version: BridgeProtocolVersion) : ProtocolNegotiation
}

sealed interface BridgeConnectionStatus {
    data object HaloNotInstalled : BridgeConnectionStatus
    data object BridgeNotSupported : BridgeConnectionStatus
    data object SignatureMismatch : BridgeConnectionStatus
    data object ConsentRequired : BridgeConnectionStatus
    data object ProtocolMismatch : BridgeConnectionStatus
    data object Busy : BridgeConnectionStatus
    data object Reconnecting : BridgeConnectionStatus
    data object Ready : BridgeConnectionStatus
    data object UnknownConnectionFailure : BridgeConnectionStatus
    data class Unsupported(val wireValue: String) : BridgeConnectionStatus

    companion object {
        fun decode(raw: String): BridgeConnectionStatus = when (raw) {
            "HALO_NOT_INSTALLED" -> HaloNotInstalled
            "BRIDGE_NOT_SUPPORTED" -> BridgeNotSupported
            "SIGNATURE_MISMATCH" -> SignatureMismatch
            "CONSENT_REQUIRED" -> ConsentRequired
            "PROTOCOL_MISMATCH" -> ProtocolMismatch
            "BUSY" -> Busy
            "RECONNECTING" -> Reconnecting
            "READY" -> Ready
            "UNKNOWN_CONNECTION_FAILURE" -> UnknownConnectionFailure
            else -> Unsupported(raw.take(BridgeLimits.MAX_UNKNOWN_CODE_CHARS))
        }
    }
}

enum class BridgeFeature {
    MODEL_INVENTORY,
    ARTIFACT_LEASES,
    TEXT_EXECUTION,
    SPEECH_EXECUTION,
    EMBEDDING_EXECUTION,
    VISION_EXECUTION,
    ACQUISITION,
    EVENT_REPLAY,
    EVIDENCE_ARTIFACT_READ,
}

enum class BridgeConsentScope { MODEL_INSPECTION, EXPLICIT_ACQUISITION, LOCAL_TEST_EXECUTION }

data class BridgeHandshakeRequest(
    val protocolVersion: BridgeProtocolVersion,
    val requestId: String,
    val clientProcessInstanceId: String,
    val clientSourceId: String,
    val clientSchemaSha256: String,
    val requestedFeatures: Set<BridgeFeature>,
) {
    init {
        require(isBoundedId(requestId) && isBoundedId(clientProcessInstanceId))
        require(isBoundedId(clientSourceId))
        require(SHA256_HEX.matches(clientSchemaSha256))
        require(requestedFeatures.size <= BridgeFeature.entries.size)
    }
}

data class BridgeRequestEnvelope<T>(
    val protocolVersion: BridgeProtocolVersion,
    val requestId: String,
    val clientProcessInstanceId: String,
    val payload: T,
    val leaseToken: String?,
) {
    init {
        require(isBoundedId(requestId) && isBoundedId(clientProcessInstanceId))
        require(leaseToken == null || isBoundedId(leaseToken))
    }
}

data class AcquireArtifactLeaseRequest(val providerId: String, val modelId: String, val generationId: String) {
    init { require(listOf(providerId, modelId, generationId).all(::isBoundedId)) }
}

data class ArtifactLease(
    val leaseId: String,
    val providerId: String,
    val modelId: String,
    val generationId: String,
    val manifest: ArtifactManifest,
    val serverEpoch: String,
    val serverBootId: String,
    val expiresAtElapsedRealtimeMs: Long,
) {
    init {
        require(listOf(leaseId, providerId, modelId, generationId, serverEpoch, serverBootId).all(::isBoundedId))
        require(manifest.providerId == providerId && manifest.modelId == modelId && manifest.generationId == generationId)
        require(expiresAtElapsedRealtimeMs > 0L)
    }
}

data class SubmitCaseRequest(
    val sessionId: String,
    val attemptId: String,
    val queueItemId: String,
    val planDigestSha256: String,
    val origin: EvidenceOrigin,
    val casePlan: RealCasePlan,
    val executionScope: ExecutionScope,
    val stageBudgets: StageBudgets,
) {
    init {
        require(isUuid(sessionId) && isUuid(attemptId) && isUuid(queueItemId))
        require(SHA256_HEX.matches(planDigestSha256))
        require(origin == EvidenceOrigin.DEVICE_MEASUREMENT)
        require(casePlan.queueItemId == queueItemId)
        require(executionScope.artifactManifestSha256 == casePlan.artifactManifest?.canonicalSha256())
    }
}

data class QueryCaseRequest(val remoteRunId: String) {
    init { require(isBoundedId(remoteRunId)) }
}

data class CancelCaseRequest(val remoteRunId: String, val attemptId: String) {
    init { require(isBoundedId(remoteRunId) && isUuid(attemptId)) }
}

data class ReadEventsRequest(val remoteRunId: String, val afterSequence: Long, val maximumEvents: Int) {
    init {
        require(isBoundedId(remoteRunId))
        require(afterSequence >= 0L && maximumEvents in 1..BridgeLimits.MAX_EVENTS_PER_PAGE)
    }
}

data class AcknowledgeEventsRequest(val remoteRunId: String, val highestContiguousSequence: Long) {
    init { require(isBoundedId(remoteRunId) && highestContiguousSequence >= 0L) }
}

data class AcquisitionRequest(val providerId: String, val modelId: String, val generationId: String?) {
    init {
        require(isBoundedId(providerId) && isBoundedId(modelId))
        require(generationId == null || isBoundedId(generationId))
    }
}

enum class AcquisitionState { NOT_REQUESTED, QUEUED, RUNNING, VERIFYING, READY, FAILED, CANCELLED, UNKNOWN }

data class AcquisitionSnapshot(
    val providerId: String,
    val modelId: String,
    val generationId: String?,
    val state: AcquisitionState,
    val progressBytes: Long?,
    val expectedBytes: Long?,
    val reasonCode: String?,
) {
    init {
        require(isBoundedId(providerId) && isBoundedId(modelId))
        require(generationId == null || isBoundedId(generationId))
        require(progressBytes == null || progressBytes >= 0L)
        require(expectedBytes == null || expectedBytes >= 0L)
        require(reasonCode == null || isBoundedId(reasonCode))
    }
}

data class EvidenceArtifactRequest(val remoteRunId: String, val artifactId: String) {
    init { require(isBoundedId(remoteRunId) && isBoundedId(artifactId)) }
}

data class RemoteCaseSnapshot(
    val remoteRunId: String,
    val stateCode: String,
    val lastEventSequence: Long,
    val stopReceipt: StopReceipt?,
) {
    init {
        require(isBoundedId(remoteRunId) && isBoundedId(stateCode))
        require(lastEventSequence >= 0L)
    }
}

enum class BridgeEventKind { SESSION_ACCEPTED, ATTEMPT_STAGE, ATTEMPT_TERMINAL, STOP_RECEIPT, ARTIFACT, EXECUTOR_DIAGNOSTIC }

data class BridgeEventPayload(
    val kind: BridgeEventKind,
    val attemptId: String?,
    val stage: TestStage?,
    val eventCode: String,
    val outcome: AttemptOutcome?,
    val validity: ResultValidity?,
    val failureCode: String?,
    val failureDomain: FailureDomain?,
    val stopDisposition: com.slafftrosheen.halomodellab.domain.StopDisposition?,
    val workerInstanceId: String?,
    val artifactId: String?,
    val artifactBytes: Long?,
    val artifactSha256: String?,
    val unavailableReason: String?,
    val outputPreview: String? = null,
    val outputBytes: Long? = null,
    val outputSha256: String? = null,
    val outputTruncated: Boolean? = null,
    val loadMs: Long? = null,
    val timeToFirstTokenMs: Long? = null,
    val generationMs: Long? = null,
    val effectiveBackendCode: String? = null,
    val runtimeVersion: String? = null,
) {
    init {
        require(isBoundedId(eventCode))
        require(attemptId == null || isUuid(attemptId))
        require(failureCode == null || isBoundedId(failureCode))
        require(workerInstanceId == null || isBoundedId(workerInstanceId))
        require(artifactId == null || isBoundedId(artifactId))
        require(artifactBytes == null || artifactBytes >= 0L)
        require(artifactSha256 == null || SHA256_HEX.matches(artifactSha256))
        require(unavailableReason == null || isBoundedId(unavailableReason))
        require(outputPreview == null || outputPreview.length <= BridgeLimits.MAX_OUTPUT_PREVIEW_CHARS)
        require(outputBytes == null || outputBytes >= 0L)
        require(outputSha256 == null || SHA256_HEX.matches(outputSha256))
        require(loadMs == null || loadMs >= 0L)
        require(timeToFirstTokenMs == null || timeToFirstTokenMs >= 0L)
        require(generationMs == null || generationMs >= 0L)
        require(effectiveBackendCode == null || isBoundedId(effectiveBackendCode))
        require(runtimeVersion == null || isBoundedId(runtimeVersion))
        val outputFieldsPresent = listOf(outputPreview != null, outputBytes != null, outputSha256 != null, outputTruncated != null)
        require(outputFieldsPresent.all { it } || outputFieldsPresent.none { it })
        require(outputPreview == null || outputBytes == outputPreview.toByteArray(Charsets.UTF_8).size.toLong())
        require((failureCode == null) == (failureDomain == null))
        when (kind) {
            BridgeEventKind.SESSION_ACCEPTED -> require(attemptId == null && stage == null && outcome == null)
            BridgeEventKind.ATTEMPT_STAGE -> require(attemptId != null && stage != null && outcome == null)
            BridgeEventKind.ATTEMPT_TERMINAL -> require(attemptId != null && outcome != null && validity != null)
            BridgeEventKind.STOP_RECEIPT -> require(attemptId != null && stopDisposition != null && outcome == null)
            BridgeEventKind.ARTIFACT -> require(
                attemptId != null && artifactId != null &&
                    ((artifactSha256 != null && artifactBytes != null) xor (unavailableReason != null)),
            )
            BridgeEventKind.EXECUTOR_DIAGNOSTIC -> Unit
        }
        require(canonicalJson().toByteArray(Charsets.UTF_8).size <= BridgeLimits.MAX_EVENT_PAYLOAD_BYTES)
    }

    fun canonicalJson(): String = buildString {
        append('{')
        appendStringField("kind", kind.name)
        append(',')
        appendNullableField("attemptId", attemptId)
        append(',')
        appendNullableField("stage", stage?.name)
        append(',')
        appendStringField("eventCode", eventCode)
        append(',')
        appendNullableField("outcome", outcome?.name)
        append(',')
        appendNullableField("validity", validity?.name)
        append(',')
        appendNullableField("failureCode", failureCode)
        append(',')
        appendNullableField("failureDomain", failureDomain?.name)
        append(',')
        appendNullableField("stopDisposition", stopDisposition?.name)
        append(',')
        appendNullableField("workerInstanceId", workerInstanceId)
        append(',')
        appendNullableField("artifactId", artifactId)
        append(",\"artifactBytes\":").append(artifactBytes?.toString() ?: "null").append(',')
        appendNullableField("artifactSha256", artifactSha256)
        append(',')
        appendNullableField("unavailableReason", unavailableReason)
        append(',')
        appendNullableField("outputPreview", outputPreview)
        append(",\"outputBytes\":").append(outputBytes?.toString() ?: "null").append(',')
        appendNullableField("outputSha256", outputSha256)
        append(',')
        append("\"outputTruncated\":").append(outputTruncated?.toString() ?: "null")
        append(',')
        append("\"loadMs\":").append(loadMs?.toString() ?: "null")
        append(',')
        append("\"timeToFirstTokenMs\":").append(timeToFirstTokenMs?.toString() ?: "null")
        append(',')
        append("\"generationMs\":").append(generationMs?.toString() ?: "null")
        append(',')
        appendNullableField("effectiveBackendCode", effectiveBackendCode)
        append(',')
        appendNullableField("runtimeVersion", runtimeVersion)
        append('}')
    }

    private fun StringBuilder.appendStringField(name: String, value: String) {
        append(jsonString(name)).append(':').append(jsonString(value))
    }

    private fun StringBuilder.appendNullableField(name: String, value: String?) {
        append(jsonString(name)).append(':').append(value?.let(::jsonString) ?: "null")
    }
}

data class BridgeRemoteEvent(val key: RemoteEventKey, val payload: BridgeEventPayload, val payloadSha256: String) {
    val payloadJson: String get() = payload.canonicalJson()

    fun canonicalEnvelopeJson(): String = buildString {
        append('{')
        appendStringField("serverEpoch", key.serverEpoch)
        append(',')
        appendStringField("remoteRunId", key.remoteRunId)
        append(",\"sequence\":").append(key.sequence).append(',')
        appendStringField("payloadSha256", payloadSha256)
        append(",\"payload\":").append(payloadJson).append('}')
    }

    init { require(SHA256_HEX.matches(payloadSha256)) }

    private fun StringBuilder.appendStringField(name: String, value: String) {
        append(jsonString(name)).append(':').append(jsonString(value))
    }
}

data class BridgeEventPage(val events: List<BridgeRemoteEvent>) {
    init {
        BridgeLimits.requireEventPage(
            payloads = events.map { it.payloadJson.toByteArray(Charsets.UTF_8) },
            encodedEnvelopes = events.map { it.canonicalEnvelopeJson().toByteArray(Charsets.UTF_8) },
        )
    }
}

data class BridgeHandshakeReply(
    val status: BridgeConnectionStatus,
    val protocolVersion: BridgeProtocolVersion?,
    val serverEpoch: String?,
    val serverInstanceId: String?,
    val serverSourceId: String?,
    val serverSchemaSha256: String?,
    val grantedConsent: Set<BridgeConsentScope>,
    val supportedFeatures: Set<BridgeFeature>,
    val runtimeCapabilities: List<RuntimeCapabilities>,
) {
    init {
        require(serverEpoch == null || isBoundedId(serverEpoch))
        require(serverInstanceId == null || isBoundedId(serverInstanceId))
        require(serverSourceId == null || isBoundedId(serverSourceId))
        require(serverSchemaSha256 == null || SHA256_HEX.matches(serverSchemaSha256))
        require(runtimeCapabilities.size <= BridgeLimits.MAX_RUNTIME_DESCRIPTORS)
        if (status == BridgeConnectionStatus.Ready) {
            require(protocolVersion != null && serverEpoch != null && serverInstanceId != null && serverSchemaSha256 != null)
        } else {
            require(serverEpoch == null && serverInstanceId == null)
        }
    }
}

data class ModelInventoryEntry(
    val providerId: String,
    val modelId: String,
    val generationId: String?,
    val state: ModelInventoryState,
    val manifest: ArtifactManifest?,
    val unavailableReason: String?,
    val roles: Set<String>,
    val backendCodes: Set<String>,
) {
    init {
        require(isBoundedId(providerId) && isBoundedId(modelId))
        require(generationId == null || isBoundedId(generationId))
        require(roles.size <= 16 && backendCodes.size <= 16)
        require(roles.all(::isBoundedId) && backendCodes.all(::isBoundedId))
        require(manifest == null || (manifest.providerId == providerId && manifest.modelId == modelId))
        require(manifest == null || generationId == null || manifest.generationId == generationId)
        if (state == ModelInventoryState.INSTALLED_VERIFIED) {
            require(generationId != null && manifest != null && unavailableReason == null)
        }
    }
}

enum class ModelInventoryState {
    INSTALLED_UNVERIFIED,
    INSTALLED_VERIFIED,
    PARTIAL,
    CORRUPT,
    MISSING,
    UNSUPPORTED,
    MANUAL_IMPORT_ONLY,
}

data class ModelInventoryPage(
    val entries: List<ModelInventoryEntry>,
    val nextPageToken: String?,
) {
    init {
        require(entries.size <= BridgeLimits.MAX_MODELS_PER_PAGE)
        require(nextPageToken == null || isBoundedId(nextPageToken))
    }
}

sealed interface BridgeOperationResult<out T> {
    data class Success<T>(val value: T) : BridgeOperationResult<T>
    data class Accepted(val requestId: String) : BridgeOperationResult<Nothing>
    data class Pending(val requestId: String, val stableReasonCode: String) : BridgeOperationResult<Nothing>
    data class Rejected(val requestId: String?, val stableReasonCode: String) : BridgeOperationResult<Nothing>
    data class Unavailable(val status: BridgeConnectionStatus) : BridgeOperationResult<Nothing>
}

/** Domain-facing API. A Binder adapter may implement it only after wire compatibility is frozen. */
interface HaloLabGateway {
    suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeReply
    suspend fun listModels(pageToken: String?): BridgeOperationResult<ModelInventoryPage>
    suspend fun acquireArtifactLease(request: BridgeRequestEnvelope<AcquireArtifactLeaseRequest>): BridgeOperationResult<ArtifactLease>
    suspend fun renewArtifactLease(request: BridgeRequestEnvelope<String>): BridgeOperationResult<ArtifactLease>
    suspend fun releaseArtifactLease(request: BridgeRequestEnvelope<String>): BridgeOperationResult<Unit>
    suspend fun submitCase(request: BridgeRequestEnvelope<SubmitCaseRequest>): BridgeOperationResult<String>
    suspend fun queryCase(request: BridgeRequestEnvelope<QueryCaseRequest>): BridgeOperationResult<RemoteCaseSnapshot>
    suspend fun cancelCase(request: BridgeRequestEnvelope<CancelCaseRequest>): BridgeOperationResult<StopReceipt>
    suspend fun readEvents(request: BridgeRequestEnvelope<ReadEventsRequest>): BridgeOperationResult<BridgeEventPage>
    suspend fun acknowledgeEvents(request: BridgeRequestEnvelope<AcknowledgeEventsRequest>): BridgeOperationResult<Unit>
    suspend fun acquireModel(request: BridgeRequestEnvelope<AcquisitionRequest>): BridgeOperationResult<String>
    suspend fun queryAcquisition(request: BridgeRequestEnvelope<AcquisitionRequest>): BridgeOperationResult<AcquisitionSnapshot>
    suspend fun cancelAcquisition(request: BridgeRequestEnvelope<AcquisitionRequest>): BridgeOperationResult<Unit>
    suspend fun openEvidenceArtifact(request: EvidenceArtifactRequest): ParcelFileDescriptor?
}

object BridgeLimits {
    const val MAX_REQUEST_BYTES = 32 * 1024
    const val MAX_REPLY_BYTES = 64 * 1024
    const val MAX_EVENT_PAGE_BYTES = 64 * 1024
    const val MAX_EVENT_PAYLOAD_BYTES = 16 * 1024
    const val MAX_EVENTS_PER_PAGE = 64
    const val MAX_MODELS_PER_PAGE = 50
    const val MAX_RUNTIME_DESCRIPTORS = 16
    const val MAX_OUTSTANDING_COMMANDS = 4
    const val MAX_CONCURRENT_ARTIFACT_TRANSFERS = 2
    const val MAX_UNKNOWN_CODE_CHARS = 128
    const val MAX_OUTPUT_PREVIEW_CHARS = 512

    fun requireRequestSize(payload: ByteArray) {
        if (payload.size > MAX_REQUEST_BYTES) throw BridgePayloadLimitException("REQUEST_TOO_LARGE")
    }

    fun requireReplySize(payload: ByteArray) {
        if (payload.size > MAX_REPLY_BYTES) throw BridgePayloadLimitException("REPLY_TOO_LARGE")
    }

    fun requireEventPage(payloads: List<ByteArray>) {
        if (payloads.size > MAX_EVENTS_PER_PAGE) throw BridgePayloadLimitException("TOO_MANY_EVENTS")
        if (payloads.any { it.size > MAX_EVENT_PAYLOAD_BYTES }) {
            throw BridgePayloadLimitException("EVENT_TOO_LARGE")
        }
        val total = payloads.fold(0L) { current, bytes -> current + bytes.size }
        if (total > MAX_EVENT_PAGE_BYTES) throw BridgePayloadLimitException("EVENT_PAGE_TOO_LARGE")
    }

    fun requireEventPage(payloads: List<ByteArray>, encodedEnvelopes: List<ByteArray>) {
        requireEventPage(payloads)
        if (encodedEnvelopes.size != payloads.size) throw BridgePayloadLimitException("EVENT_PAGE_INVALID")
        val encodedTotal = encodedEnvelopes.fold(0L) { current, bytes -> current + bytes.size }
        if (encodedTotal > MAX_EVENT_PAGE_BYTES) throw BridgePayloadLimitException("EVENT_PAGE_TOO_LARGE")
    }

    fun requireModelPage(entries: List<ModelInventoryEntry>, encodedBytes: Int) {
        if (entries.size > MAX_MODELS_PER_PAGE) throw BridgePayloadLimitException("TOO_MANY_MODELS")
        if (encodedBytes > MAX_REPLY_BYTES) throw BridgePayloadLimitException("REPLY_TOO_LARGE")
    }
}

class BridgePayloadLimitException(val code: String) : IllegalArgumentException(code)

private val SHA256_HEX = Regex("[0-9a-f]{64}")
private val SAFE_ID = Regex("[A-Za-z0-9._:/@+-]{1,256}")
private fun isBoundedId(value: String): Boolean = SAFE_ID.matches(value)
private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u").append(char.code.toString(16).padStart(4, '0')) else append(char)
        }
    }
    append('"')
}
private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
private fun isUuid(value: String): Boolean = UUID_PATTERN.matches(value)
