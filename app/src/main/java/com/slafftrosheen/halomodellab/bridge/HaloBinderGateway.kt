package com.slafftrosheen.halomodellab.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.halo.lab.bridge.v1.IHaloLabBridge
import com.slafftrosheen.halomodellab.profiles.TextProfileRegistry
import com.slafftrosheen.halomodellab.domain.ArtifactManifest
import com.slafftrosheen.halomodellab.domain.ArtifactPart
import com.slafftrosheen.halomodellab.domain.RemoteEventKey
import com.slafftrosheen.halomodellab.domain.StopDisposition
import com.slafftrosheen.halomodellab.domain.StopReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

/** Thin client for Halo's signature-protected AIDL service. Lab never opens model paths or bytes. */
class HaloBinderGateway(context: Context) : HaloLabGateway {
    private val appContext = context.applicationContext

    override suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeReply = try { invoke(
        requestJson = JSONObject()
            .put("protocolVersion", JSONObject().put("major", request.protocolVersion.major).put("minor", request.protocolVersion.minor))
            .put("requestId", request.requestId).put("clientProcessInstanceId", request.clientProcessInstanceId)
            .put("clientSourceId", request.clientSourceId).put("clientSchemaSha256", request.clientSchemaSha256)
            .put("requestedFeatures", request.requestedFeatures.toJsonArray()).toString(),
        operation = IHaloLabBridge::handshake,
    ).let { response ->
        val status = decodeConnectionStatus(response)
        val version = response.optJSONObject("protocolVersion")?.let {
            BridgeProtocolVersion(it.optInt("major"), it.optInt("minor"))
        }
        BridgeHandshakeReply(
            status = status,
            protocolVersion = version,
            serverEpoch = response.optStringOrNull("serverEpoch"),
            serverInstanceId = response.optStringOrNull("serverInstanceId"),
            serverSourceId = response.optStringOrNull("serverSourceId"),
            serverSchemaSha256 = response.optStringOrNull("serverSchemaSha256"),
            grantedConsent = response.optStringSet("grantedConsent").mapNotNull { runCatching { BridgeConsentScope.valueOf(it) }.getOrNull() }.toSet(),
            supportedFeatures = response.optStringSet("supportedFeatures").mapNotNull { runCatching { BridgeFeature.valueOf(it) }.getOrNull() }.toSet(),
            runtimeCapabilities = response.optJSONArray("runtimeCapabilities")?.let { array ->
                (0 until array.length()).map { array.getJSONObject(it) }.map(::parseRuntimeCapabilities)
            }.orEmpty(),
        )
    } } catch (failure: BridgeUnavailableException) {
        BridgeHandshakeReply(failure.status, null, null, null, null, null, emptySet(), emptySet(), emptyList())
    }

    override suspend fun listModels(pageToken: String?): BridgeOperationResult<ModelInventoryPage> = operation(
        JSONObject().put("pageToken", pageToken ?: JSONObject.NULL), IHaloLabBridge::listModels,
    ) { response ->
        val entries = response.optJSONArray("entries") ?: JSONArray()
        ModelInventoryPage(
            entries = (0 until entries.length()).map { index ->
                val row = entries.getJSONObject(index)
                ModelInventoryEntry(
                    providerId = row.getString("providerId"),
                    modelId = row.getString("modelId"),
                    generationId = row.optStringOrNull("generationId"),
                    state = ModelInventoryState.valueOf(row.getString("state")),
                    manifest = row.optJSONObject("manifest")?.let(::parseManifest),
                    unavailableReason = row.optStringOrNull("unavailableReason"),
                    roles = row.optStringSet("roles"),
                    backendCodes = row.optStringSet("backendCodes"),
                )
            },
            nextPageToken = response.optStringOrNull("nextPageToken"),
        )
    }

    override suspend fun acquireArtifactLease(request: BridgeRequestEnvelope<AcquireArtifactLeaseRequest>): BridgeOperationResult<ArtifactLease> = operation(
        JSONObject().put("providerId", request.payload.providerId).put("modelId", request.payload.modelId)
            .put("generationId", request.payload.generationId), IHaloLabBridge::acquireArtifactLease,
    ) { row -> parseArtifactLease(row) }

    override suspend fun renewArtifactLease(request: BridgeRequestEnvelope<String>): BridgeOperationResult<ArtifactLease> = operation(
        JSONObject().put("leaseId", request.payload), IHaloLabBridge::renewArtifactLease,
    ) { row ->
        // The server renews expiry only. The caller retains the immutable original descriptor.
        ArtifactLease(
            leaseId = request.payload,
            providerId = row.optString("providerId", "provider.litert.local"),
            modelId = row.optString("modelId", "unknown"),
            generationId = row.optString("generationId", "unknown"),
            manifest = row.optJSONObject("manifest")?.let(::parseManifest)
                ?: throw IllegalArgumentException("RENEW_REPLY_MISSING_MANIFEST"),
            serverEpoch = row.optString("serverEpoch", "unknown"),
            serverBootId = row.optString("serverBootId", "unknown"),
            expiresAtElapsedRealtimeMs = row.getLong("expiresAtElapsedRealtimeMs"),
        )
    }

    override suspend fun releaseArtifactLease(request: BridgeRequestEnvelope<String>): BridgeOperationResult<Unit> = operation(
        JSONObject().put("leaseId", request.payload), IHaloLabBridge::releaseArtifactLease,
    ) { Unit }

    override suspend fun submitCase(request: BridgeRequestEnvelope<SubmitCaseRequest>): BridgeOperationResult<String> {
        val payload = request.payload
        val case = payload.casePlan
        val fixture = when {
            case.suiteId == "fast-smoke" && case.suiteVersion == "1" && case.caseId == "short-ready" -> "fast-smoke-1-short-ready"
            else -> return BridgeOperationResult.Rejected(payload.attemptId, "CASE_UNSUPPORTED_BY_HALO_V1")
        }
        val suite = TextProfileRegistry.resolve(case.suiteId, case.suiteVersion)
        if (suite == null || suite.fixtureManifestSha256() != case.fixtureManifestSha256 ||
            case.role != com.slafftrosheen.halomodellab.domain.ModelRole.FAST ||
            case.requestedBackend != com.slafftrosheen.halomodellab.domain.RealBackend.CPU ||
            payload.executionScope.requestedBackend != com.slafftrosheen.halomodellab.domain.RealBackend.CPU ||
            case.maximumOutputTokens != 8) return BridgeOperationResult.Rejected(payload.attemptId, "CASE_CONFIGURATION_UNSUPPORTED")
        val manifest = case.artifactManifest
            ?: return BridgeOperationResult.Rejected(payload.attemptId, "ARTIFACT_MANIFEST_REQUIRED")
        if (payload.origin != com.slafftrosheen.halomodellab.domain.EvidenceOrigin.DEVICE_MEASUREMENT ||
            manifest.providerId != "provider.litert.local") return BridgeOperationResult.Rejected(payload.attemptId, "REAL_LOCAL_CASE_REQUIRED")
        val mainArtifact = manifest.parts.firstOrNull { it.relativeName.endsWith(".litertlm") }
            ?: return BridgeOperationResult.Rejected(payload.attemptId, "MODEL_ARTIFACT_MISSING")
        return operation(
            JSONObject().put("attemptId", payload.attemptId).put("modelId", case.modelId)
                .put("artifactSha256", mainArtifact.sha256).put("fixtureId", fixture)
                .put("generationId", case.artifactGenerationId).put("leaseId", request.leaseToken)
                .put("sessionId", payload.sessionId).put("queueItemId", payload.queueItemId)
                .put("planDigestSha256", payload.planDigestSha256).put("fixtureManifestSha256", case.fixtureManifestSha256)
                .put("suiteId", case.suiteId).put("suiteVersion", case.suiteVersion).put("caseId", case.caseId)
                .put("role", case.role.name).put("requestedBackend", case.requestedBackend.name)
                .put("maximumOutputTokens", case.maximumOutputTokens)
                .put("stageBudgets", JSONObject()
                    .put("loadMs", payload.stageBudgets.loadMs)
                    .put("executeMs", payload.stageBudgets.executeMs)
                    .put("unloadMs", payload.stageBudgets.unloadMs)),
            IHaloLabBridge::submitCase,
        ) { row -> row.optString("attemptId", payload.attemptId) }
    }

    override suspend fun queryCase(request: BridgeRequestEnvelope<QueryCaseRequest>): BridgeOperationResult<RemoteCaseSnapshot> = operation(
        JSONObject().put("attemptId", request.payload.remoteRunId), IHaloLabBridge::queryCase,
    ) { row ->
        val result = row.optJSONObject("result")
        RemoteCaseSnapshot(
            remoteRunId = row.optString("attemptId", request.payload.remoteRunId),
            stateCode = row.optString("state", "UNKNOWN"),
            lastEventSequence = row.optLong("lastSequence", 0L),
            stopReceipt = result?.optJSONObject("stopReceipt")?.let(::parseStopReceipt),
        )
    }

    override suspend fun cancelCase(request: BridgeRequestEnvelope<CancelCaseRequest>): BridgeOperationResult<StopReceipt> = operation(
        JSONObject().put("attemptId", request.payload.attemptId).put("remoteRunId", request.payload.remoteRunId),
        IHaloLabBridge::cancelCase,
    ) { row ->
        StopReceipt(
            attemptId = request.payload.attemptId,
            workerInstanceId = row.optStringOrNull("workerInstanceId"),
            disposition = StopDisposition.valueOf(row.optString("disposition", "REQUEST_ACCEPTED")),
            evidenceSequence = row.optLong("evidenceSequence", 0L).takeIf { it > 0L },
        )
    }

    override suspend fun readEvents(request: BridgeRequestEnvelope<ReadEventsRequest>): BridgeOperationResult<BridgeEventPage> = operation(
        JSONObject().put("attemptId", request.payload.remoteRunId).put("afterSequence", request.payload.afterSequence)
            .put("limit", request.payload.maximumEvents), IHaloLabBridge::readEvents,
    ) { row ->
        val events = row.optJSONArray("events") ?: JSONArray()
        BridgeEventPage((0 until events.length()).map { index ->
            val envelope = events.getJSONObject(index)
            val payload = envelope.getJSONObject("payload")
            BridgeRemoteEvent(
                key = RemoteEventKey(envelope.getString("serverEpoch"), envelope.getString("remoteRunId"), envelope.getLong("sequence")),
                payload = parseEventPayload(payload),
                payloadSha256 = envelope.getString("payloadSha256"),
            )
        })
    }

    override suspend fun acknowledgeEvents(request: BridgeRequestEnvelope<AcknowledgeEventsRequest>): BridgeOperationResult<Unit> = operation(
        JSONObject().put("attemptId", request.payload.remoteRunId).put("sequence", request.payload.highestContiguousSequence),
        IHaloLabBridge::acknowledgeEvents,
    ) { Unit }

    override suspend fun acquireModel(request: BridgeRequestEnvelope<AcquisitionRequest>): BridgeOperationResult<String> = unavailable("HALO_ACQUISITION_NOT_IMPLEMENTED")
    override suspend fun queryAcquisition(request: BridgeRequestEnvelope<AcquisitionRequest>): BridgeOperationResult<AcquisitionSnapshot> = unavailable("HALO_ACQUISITION_NOT_IMPLEMENTED")
    override suspend fun cancelAcquisition(request: BridgeRequestEnvelope<AcquisitionRequest>): BridgeOperationResult<Unit> = unavailable("HALO_ACQUISITION_NOT_IMPLEMENTED")
    override suspend fun openEvidenceArtifact(request: EvidenceArtifactRequest): ParcelFileDescriptor? = withContext(Dispatchers.IO) {
        val requestJson = JSONObject()
            .put("remoteRunId", request.remoteRunId)
            .put("artifactId", request.artifactId)
            .toString()
        try {
            BridgeLimits.requireRequestSize(requestJson.toByteArray(Charsets.UTF_8))
            val remote = bind()
            try {
                remote.api.openEvidenceArtifact(requestJson)
            } finally {
                runCatching { appContext.unbindService(remote.connection) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun <T> operation(
        payload: JSONObject,
        call: (IHaloLabBridge, String) -> String,
        parse: (JSONObject) -> T,
    ): BridgeOperationResult<T> = try {
        val response = invoke(payload.toString(), call)
        when (response.optString("status")) {
            "SUCCESS" -> BridgeOperationResult.Success(parse(response))
            "ACCEPTED" -> BridgeOperationResult.Accepted(response.optString("attemptId", response.optString("requestId", "unknown")))
            "PENDING", "STOPPING" -> BridgeOperationResult.Pending(response.optString("attemptId", "unknown"), response.optString("code", response.optString("status")))
            "UNSUPPORTED" -> BridgeOperationResult.Rejected(null, response.optString("reason", "UNSUPPORTED"))
            "ERROR" -> when (decodeConnectionStatus(response)) {
                BridgeConnectionStatus.ConsentRequired -> BridgeOperationResult.Unavailable(BridgeConnectionStatus.ConsentRequired)
                BridgeConnectionStatus.ProtocolMismatch -> BridgeOperationResult.Unavailable(BridgeConnectionStatus.ProtocolMismatch)
                else -> BridgeOperationResult.Rejected(response.optStringOrNull("attemptId"), response.optString("code", "HALO_REQUEST_REJECTED"))
            }
            else -> BridgeOperationResult.Rejected(response.optStringOrNull("attemptId"), response.optString("code", "HALO_REQUEST_REJECTED"))
        }
    } catch (failure: BridgeUnavailableException) {
        BridgeOperationResult.Unavailable(failure.status)
    } catch (_: Exception) {
        BridgeOperationResult.Unavailable(BridgeConnectionStatus.UnknownConnectionFailure)
    }

    private suspend fun invoke(requestJson: String, operation: (IHaloLabBridge, String) -> String): JSONObject =
        withContext(Dispatchers.IO) {
            BridgeLimits.requireRequestSize(requestJson.toByteArray(Charsets.UTF_8))
            val remote = bind()
            try {
                val raw = operation(remote.api, requestJson)
                BridgeLimits.requireReplySize(raw.toByteArray(Charsets.UTF_8))
                JSONObject(raw)
            } finally {
                runCatching { appContext.unbindService(remote.connection) }
            }
        }

    private suspend fun bind(): BoundBridge = suspendCancellableCoroutine { continuation ->
        val intent = Intent("com.halo.lab.bridge.v1.BIND").setPackage(HALO_PACKAGE)
        try {
            appContext.packageManager.getPackageInfo(HALO_PACKAGE, PackageManager.GET_SERVICES)
        } catch (_: PackageManager.NameNotFoundException) {
            continuation.resumeWith(Result.failure(BridgeUnavailableException(BridgeConnectionStatus.HaloNotInstalled)))
            return@suspendCancellableCoroutine
        }
        // The binding is package-scoped and the service filter is a versioned IPC action, not a
        // launcher/default action. MATCH_DEFAULT_ONLY incorrectly hides that valid service.
        if (appContext.packageManager.resolveService(intent, 0) == null) {
            continuation.resumeWith(Result.failure(BridgeUnavailableException(BridgeConnectionStatus.BridgeNotSupported)))
            return@suspendCancellableCoroutine
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val bridge = service?.let(IHaloLabBridge.Stub::asInterface)
                val value = bridge?.let { BoundBridge(it, this) }
                if (continuation.isActive) {
                    if (value == null) continuation.resumeWith(Result.failure(BridgeUnavailableException(BridgeConnectionStatus.BridgeNotSupported)))
                    else continuation.resume(value)
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(BridgeUnavailableException(BridgeConnectionStatus.Reconnecting)))
            }
            override fun onBindingDied(name: ComponentName?) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(BridgeUnavailableException(BridgeConnectionStatus.Reconnecting)))
            }
            override fun onNullBinding(name: ComponentName?) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(BridgeUnavailableException(BridgeConnectionStatus.BridgeNotSupported)))
            }
        }
        val bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            continuation.resumeWith(Result.failure(BridgeUnavailableException(BridgeConnectionStatus.SignatureMismatch)))
            return@suspendCancellableCoroutine
        }
        if (!bound) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(BridgeUnavailableException(BridgeConnectionStatus.BridgeNotSupported)))
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation { runCatching { appContext.unbindService(connection) } }
    }

    private data class BoundBridge(val api: IHaloLabBridge, val connection: ServiceConnection)

    private fun parseArtifactLease(row: JSONObject): ArtifactLease = ArtifactLease(
        leaseId = row.getString("leaseId"), providerId = row.getString("providerId"), modelId = row.getString("modelId"),
        generationId = row.getString("generationId"), manifest = parseManifest(row.getJSONObject("manifest")),
        serverEpoch = row.getString("serverEpoch"), serverBootId = row.getString("serverBootId"),
        expiresAtElapsedRealtimeMs = row.getLong("expiresAtElapsedRealtimeMs"),
    )

    private fun parseManifest(row: JSONObject) = ArtifactManifest(
        providerId = row.getString("providerId"), modelId = row.getString("modelId"), generationId = row.getString("generationId"),
        parts = row.getJSONArray("parts").let { array -> (0 until array.length()).map { i -> array.getJSONObject(i) }.map {
            ArtifactPart(it.getString("relativeName"), it.getString("sha256"), it.getLong("byteCount"))
        } },
    )

    private fun parseRuntimeCapabilities(row: JSONObject): com.slafftrosheen.halomodellab.domain.RuntimeCapabilities {
        val descriptors = row.getJSONArray("capabilities")
        return com.slafftrosheen.halomodellab.domain.RuntimeCapabilities(
            capturedAtWallTimeMs = row.getLong("capturedAtWallTimeMs"),
            haloSourceId = row.getString("haloSourceId"), runtimeId = row.getString("runtimeId"),
            runtimeVersion = row.getString("runtimeVersion"), adapterCompatibilityId = row.getString("adapterCompatibilityId"),
            capabilities = (0 until descriptors.length()).map { descriptors.getJSONObject(it) }.map { capability ->
                com.slafftrosheen.halomodellab.domain.RuntimeCapability(
                    role = com.slafftrosheen.halomodellab.domain.ModelRole.valueOf(capability.getString("role")),
                    backends = capability.optJSONArray("backends")?.let { values ->
                        (0 until values.length()).map { index -> com.slafftrosheen.halomodellab.domain.RealBackend.valueOf(values.getString(index)) }.toSet()
                    }.orEmpty(),
                    supportsStreaming = capability.getBoolean("supportsStreaming"),
                    supportsToolCalls = capability.getBoolean("supportsToolCalls"),
                    maximumInputTokens = if (capability.isNull("maximumInputTokens")) null else capability.getInt("maximumInputTokens"),
                    maximumOutputTokens = if (capability.isNull("maximumOutputTokens")) null else capability.getInt("maximumOutputTokens"),
                )
            },
            snapshotSha256 = row.getString("snapshotSha256"),
        )
    }

    private fun parseStopReceipt(row: JSONObject) = StopReceipt(
        attemptId = row.getString("attemptId"), workerInstanceId = row.optStringOrNull("workerInstanceId"),
        disposition = StopDisposition.valueOf(row.getString("disposition")),
        evidenceSequence = row.optLong("evidenceSequence", 0L).takeIf { it > 0L },
    )

    private fun parseEventPayload(row: JSONObject) = BridgeEventPayload(
        kind = BridgeEventKind.valueOf(row.getString("kind")), attemptId = row.optStringOrNull("attemptId"),
        stage = row.optStringOrNull("stage")?.let { com.slafftrosheen.halomodellab.domain.TestStage.valueOf(it) },
        eventCode = row.getString("eventCode"),
        outcome = row.optStringOrNull("outcome")?.let { com.slafftrosheen.halomodellab.domain.AttemptOutcome.valueOf(it) },
        validity = row.optStringOrNull("validity")?.let { com.slafftrosheen.halomodellab.domain.ResultValidity.valueOf(it) },
        failureCode = row.optStringOrNull("failureCode"),
        failureDomain = row.optStringOrNull("failureDomain")?.let { com.slafftrosheen.halomodellab.domain.FailureDomain.valueOf(it) },
        stopDisposition = row.optStringOrNull("stopDisposition")?.let { StopDisposition.valueOf(it) },
        workerInstanceId = row.optStringOrNull("workerInstanceId"), artifactId = row.optStringOrNull("artifactId"),
        artifactBytes = if (row.isNull("artifactBytes")) null else row.optLong("artifactBytes"),
        artifactSha256 = row.optStringOrNull("artifactSha256"), unavailableReason = row.optStringOrNull("unavailableReason"),
        outputPreview = row.optStringOrNull("outputPreview"),
        outputBytes = if (row.isNull("outputBytes")) null else row.optLong("outputBytes"),
        outputSha256 = row.optStringOrNull("outputSha256"),
        outputTruncated = if (row.isNull("outputTruncated")) null else row.optBoolean("outputTruncated"),
        loadMs = if (row.isNull("loadMs")) null else row.optLong("loadMs"),
        timeToFirstTokenMs = if (row.isNull("timeToFirstTokenMs")) null else row.optLong("timeToFirstTokenMs"),
        generationMs = if (row.isNull("generationMs")) null else row.optLong("generationMs"),
        effectiveBackendCode = row.optStringOrNull("effectiveBackendCode"),
        runtimeVersion = row.optStringOrNull("runtimeVersion"),
    )

    private fun <T> unavailable(reason: String): BridgeOperationResult<T> =
        BridgeOperationResult.Unavailable(BridgeConnectionStatus.Unsupported(reason))

    private class BridgeUnavailableException(val status: BridgeConnectionStatus) : Exception(status.toString())

    private fun Set<BridgeFeature>.toJsonArray() = JSONArray().also { array -> forEach { array.put(it.name) } }
    private fun JSONObject.optStringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)
    private fun decodeConnectionStatus(response: JSONObject): BridgeConnectionStatus {
        val raw = response.optString("status", "UNKNOWN_CONNECTION_FAILURE")
        if (raw != "ERROR") return BridgeConnectionStatus.decode(raw)
        return when (response.optString("code")) {
            "CONSENT_REQUIRED" -> BridgeConnectionStatus.ConsentRequired
            "PROTOCOL_MISMATCH" -> BridgeConnectionStatus.ProtocolMismatch
            "CALLER_NOT_AUTHORIZED" -> BridgeConnectionStatus.SignatureMismatch
            else -> BridgeConnectionStatus.UnknownConnectionFailure
        }
    }
    private fun JSONObject.optStringSet(key: String): Set<String> = optJSONArray(key)?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }.toSet()
    }.orEmpty()

    companion object { private const val HALO_PACKAGE = "com.aistudio.braintool.xkqzps" }
}
