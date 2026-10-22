package com.slafftrosheen.halomodellab.runner

import android.os.SystemClock
import com.slafftrosheen.halomodellab.bridge.AcknowledgeEventsRequest
import com.slafftrosheen.halomodellab.bridge.AcquireArtifactLeaseRequest
import com.slafftrosheen.halomodellab.bridge.BridgeConsentScope
import com.slafftrosheen.halomodellab.bridge.BridgeFeature
import com.slafftrosheen.halomodellab.bridge.BridgeHandshakeRequest
import com.slafftrosheen.halomodellab.bridge.BridgeOperationResult
import com.slafftrosheen.halomodellab.bridge.BridgeProtocolVersion
import com.slafftrosheen.halomodellab.bridge.BridgeRequestEnvelope
import com.slafftrosheen.halomodellab.bridge.CancelCaseRequest
import com.slafftrosheen.halomodellab.bridge.HaloLabGateway
import com.slafftrosheen.halomodellab.bridge.QueryCaseRequest
import com.slafftrosheen.halomodellab.bridge.ReadEventsRequest
import com.slafftrosheen.halomodellab.bridge.SubmitCaseRequest
import com.slafftrosheen.halomodellab.data.RemoteEventImporter
import com.slafftrosheen.halomodellab.data.RemoteEventPageResult
import com.slafftrosheen.halomodellab.data.RemoteRunBindingResult
import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptResult
import com.slafftrosheen.halomodellab.domain.BenchmarkEngine
import com.slafftrosheen.halomodellab.domain.BenchmarkRequest
import com.slafftrosheen.halomodellab.domain.EvidenceConfidence
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.StopDisposition
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Supplies the immutable persisted plan, exact scope and digest for one already-created attempt. */
fun interface RealExecutionContextSource {
    suspend fun forAttempt(request: BenchmarkRequest): SubmitCaseRequest?
}

/**
 * Executes only through Halo's opt-in bridge. Every accepted remote event is imported and
 * committed before acknowledgement. The context source must return only a fixed, supported
 * plan whose artifact manifest and scope were persisted before this engine is called.
 */
class HaloRemoteBenchmarkEngine(
    private val gateway: HaloLabGateway,
    private val importer: RemoteEventImporter,
    private val contexts: RealExecutionContextSource,
    private val clientProcessInstanceId: String,
    private val clientSourceId: String,
    private val protocolSchemaSha256: String,
    private val pollIntervalMs: Long = 250L,
) : BenchmarkEngine {
    override val engineId: String = "halo-lab-bridge-v1"
    override val origin: EvidenceOrigin = EvidenceOrigin.DEVICE_MEASUREMENT
    override val managesOwnDeadline: Boolean = true

    init {
        require(pollIntervalMs in 100L..5_000L)
    }

    override suspend fun execute(
        request: BenchmarkRequest,
        events: com.slafftrosheen.halomodellab.domain.AttemptEventSink,
    ): AttemptResult {
        require(request.origin == EvidenceOrigin.DEVICE_MEASUREMENT)
        val context = contexts.forAttempt(request)
            ?: return unknown("REAL_EXECUTION_CONTEXT_MISSING", "The persisted real plan or execution scope is unavailable.")
        require(context.sessionId == request.sessionId && context.attemptId == request.attemptId)
        require(context.origin == EvidenceOrigin.DEVICE_MEASUREMENT)

        val handshake = gateway.handshake(
            BridgeHandshakeRequest(
                protocolVersion = BridgeProtocolVersion(PROTOCOL_MAJOR, PROTOCOL_MINOR),
                requestId = UUID.randomUUID().toString(),
                clientProcessInstanceId = clientProcessInstanceId,
                clientSourceId = clientSourceId,
                clientSchemaSha256 = protocolSchemaSha256,
                requestedFeatures = setOf(
                    BridgeFeature.MODEL_INVENTORY,
                    BridgeFeature.ARTIFACT_LEASES,
                    BridgeFeature.TEXT_EXECUTION,
                    BridgeFeature.EVENT_REPLAY,
                ),
            ),
        )
        if (handshake.status != com.slafftrosheen.halomodellab.bridge.BridgeConnectionStatus.Ready ||
            handshake.protocolVersion?.negotiate(BridgeProtocolVersion(PROTOCOL_MAJOR, PROTOCOL_MINOR)) !is
            com.slafftrosheen.halomodellab.bridge.ProtocolNegotiation.Compatible ||
            handshake.serverSchemaSha256 != protocolSchemaSha256 ||
            BridgeConsentScope.LOCAL_TEST_EXECUTION !in handshake.grantedConsent
        ) {
            return unknown("HALO_PREFLIGHT_REJECTED", "Halo protocol, schema, or explicit local-test consent did not match.")
        }

        val envelopeId = UUID.randomUUID().toString()
        val acquired = gateway.acquireArtifactLease(
            BridgeRequestEnvelope(
                BridgeProtocolVersion(PROTOCOL_MAJOR, PROTOCOL_MINOR), envelopeId,
                clientProcessInstanceId,
                AcquireArtifactLeaseRequest(
                    context.casePlan.providerId,
                    context.casePlan.modelId,
                    context.casePlan.artifactGenerationId,
                ),
                null,
            ),
        )
        val lease = (acquired as? BridgeOperationResult.Success)?.value
            ?: return unknown("ARTIFACT_LEASE_UNAVAILABLE", "Halo did not grant the model artifact lease.")
        if (lease.serverEpoch != handshake.serverEpoch || lease.manifest != context.casePlan.artifactManifest) {
            releaseLease(lease.leaseId)
            return unknown("ARTIFACT_LEASE_MISMATCH", "The leased artifact did not match the frozen plan.")
        }

        var acceptedRemoteId: String? = null
        var safeToReleaseLease = false
        var remoteSubmissionMayExist = false
        try {
            // Halo uses attemptId as its idempotency and remote-run key. Query first so a lost
            // submit reply cannot create a second native allocation after process recovery.
            val prior = gateway.queryCase(envelope(request, QueryCaseRequest(request.attemptId)))
            val priorSnapshot = (prior as? BridgeOperationResult.Success)?.value
            acceptedRemoteId = when {
                priorSnapshot != null && priorSnapshot.stateCode != UNKNOWN_REMOTE_STATE -> priorSnapshot.remoteRunId
                else -> null
            }
            if (acceptedRemoteId == null) {
                remoteSubmissionMayExist = true
                acceptedRemoteId = when (val submitted = gateway.submitCase(
                    BridgeRequestEnvelope(
                        BridgeProtocolVersion(PROTOCOL_MAJOR, PROTOCOL_MINOR),
                        UUID.randomUUID().toString(), clientProcessInstanceId, context, lease.leaseId,
                    ),
                )) {
                    is BridgeOperationResult.Success -> submitted.value
                    is BridgeOperationResult.Accepted -> submitted.requestId
                    is BridgeOperationResult.Pending -> submitted.requestId
                    else -> {
                        remoteSubmissionMayExist = false
                        return unknown("HALO_SUBMIT_NOT_ACCEPTED", "Halo did not accept the fixed real-model attempt.")
                    }
                }
            }
            val remoteId = checkNotNull(acceptedRemoteId)
            val binding = importer.bindAcceptedRun(
                request.sessionId,
                handshake.serverEpoch!!,
                remoteId,
                handshake.serverInstanceId!!,
            )
            if (binding != RemoteRunBindingResult.Bound) {
                return unknown("REMOTE_BINDING_CONFLICT", "The persisted session is already bound to another remote run.")
            }

            var terminal: com.slafftrosheen.halomodellab.bridge.BridgeEventPayload? = null
            var lastSequence = 0L
            val startedAt = SystemClock.elapsedRealtime()
            val deadline = startedAt + request.caseTimeoutMs
            while (terminal == null) {
                val page = gateway.readEvents(envelope(request, ReadEventsRequest(remoteId, lastSequence, 64)))
                if (page is BridgeOperationResult.Success) {
                    val imported = importer.importPage(request.sessionId, page.value.events)
                    if (imported !is RemoteEventPageResult.Imported) {
                        return unknown("REMOTE_EVENT_IMPORT_REJECTED", "Halo evidence could not be committed contiguously.")
                    }
                    lastSequence = imported.acknowledgedSequence
                    terminal = page.value.events.lastOrNull {
                        it.payload.kind == com.slafftrosheen.halomodellab.bridge.BridgeEventKind.ATTEMPT_TERMINAL &&
                            it.payload.attemptId == request.attemptId
                    }?.payload
                    if (lastSequence > 0L) {
                        gateway.acknowledgeEvents(
                            envelope(request, AcknowledgeEventsRequest(remoteId, lastSequence)),
                        )
                    }
                    page.value.events.filter { it.payload.kind == com.slafftrosheen.halomodellab.bridge.BridgeEventKind.ATTEMPT_STAGE }
                        .filter { it.payload.attemptId == request.attemptId }
                        .forEach { event -> event.payload.stage?.let { events.record(it, event.payload.eventCode) } }
                }

                if (terminal != null) break
                val queried = gateway.queryCase(envelope(request, QueryCaseRequest(remoteId)))
                val snapshot = (queried as? BridgeOperationResult.Success)?.value
                if (snapshot != null && snapshot.stateCode in TERMINAL_STATES) {
                    // The remote worker is terminal. If replay is unavailable or truncated,
                    // preserve the outcome as unknown rather than manufacture measurements.
                    terminal = null
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        return unknown("REMOTE_TERMINAL_EVENT_MISSING", "Halo finished without a replayable terminal result.")
                    }
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    stopAndConfirm(request, remoteId)
                    safeToReleaseLease = true
                    return AttemptResult(
                        AttemptOutcome.TIMED_OUT, ResultValidity.UNKNOWN, origin,
                        FailureInfo("REMOTE_DEADLINE_EXCEEDED", FailureDomain.HARNESS, EvidenceConfidence.OBSERVED,
                            "Halo's remote attempt exceeded the persisted execution deadline; cleanup was confirmed."),
                    )
                }
                delay(pollIntervalMs)
            }

            val completed = terminal ?: return unknown("REMOTE_TERMINAL_RESULT_UNKNOWN", "No terminal evidence was imported.")
            safeToReleaseLease = true
            AttemptResult(
                outcome = completed.outcome ?: AttemptOutcome.FAILED,
                validity = completed.validity ?: ResultValidity.UNKNOWN,
                origin = origin,
                failure = completed.failureCode?.let { code ->
                    FailureInfo(
                        code = code,
                        domain = completed.failureDomain ?: FailureDomain.UNKNOWN,
                        confidence = EvidenceConfidence.OBSERVED,
                        detail = completed.eventCode.take(MAX_FAILURE_DETAIL_CHARS),
                    )
                },
            )
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                if (remoteSubmissionMayExist || acceptedRemoteId != null) {
                    stopAndConfirm(request, acceptedRemoteId ?: request.attemptId)
                }
                safeToReleaseLease = true
            }
            throw cancelled
        } catch (failure: Exception) {
            withContext(NonCancellable) {
                if (remoteSubmissionMayExist || acceptedRemoteId != null) {
                    stopAndConfirm(request, acceptedRemoteId ?: request.attemptId)
                    safeToReleaseLease = true
                }
            }
            unknown("HALO_REMOTE_EXECUTION_UNKNOWN", failure.message ?: failure::class.java.simpleName)
        } finally {
            // Every exit after acceptance must first prove cleanup or worker death. This also
            // covers event-import and acknowledgement failures after a remote allocation.
            if (remoteSubmissionMayExist && !safeToReleaseLease) {
                withContext(NonCancellable) { stopAndConfirm(request, acceptedRemoteId ?: request.attemptId) }
                safeToReleaseLease = true
            }
            if (safeToReleaseLease || !remoteSubmissionMayExist) {
                withContext(NonCancellable) { releaseLease(lease.leaseId) }
            }
        }
    }

    private suspend fun stopAndConfirm(request: BenchmarkRequest, remoteId: String) {
        val stopRequest = CancelCaseRequest(remoteId, request.attemptId)
        gateway.cancelCase(envelope(request, stopRequest))
        while (true) {
            val query = gateway.queryCase(envelope(request, QueryCaseRequest(remoteId)))
            val snapshot = (query as? BridgeOperationResult.Success)?.value
            val receipt = snapshot?.stopReceipt
            if (receipt?.safeToReleaseExecutionLease == true ||
                (snapshot != null && snapshot.stateCode in TERMINAL_STATES &&
                    receipt?.disposition == StopDisposition.ALREADY_TERMINAL)
            ) {
                return
            }
            delay(pollIntervalMs)
        }
    }

    private suspend fun releaseLease(leaseId: String) {
        gateway.releaseArtifactLease(
            BridgeRequestEnvelope(
                BridgeProtocolVersion(PROTOCOL_MAJOR, PROTOCOL_MINOR), UUID.randomUUID().toString(),
                clientProcessInstanceId, leaseId, null,
            ),
        )
    }

    private fun <T> envelope(request: BenchmarkRequest, payload: T) = BridgeRequestEnvelope(
        BridgeProtocolVersion(PROTOCOL_MAJOR, PROTOCOL_MINOR), UUID.randomUUID().toString(),
        clientProcessInstanceId, payload, null,
    )

    private fun unknown(code: String, detail: String) = AttemptResult(
        AttemptOutcome.FAILED, ResultValidity.UNKNOWN, origin,
        FailureInfo(code, FailureDomain.HARNESS, EvidenceConfidence.OBSERVED, detail.take(MAX_FAILURE_DETAIL_CHARS)),
    )

    private companion object {
        const val PROTOCOL_MAJOR = 1
        const val PROTOCOL_MINOR = 0
        const val UNKNOWN_REMOTE_STATE = "UNKNOWN"
        const val MAX_FAILURE_DETAIL_CHARS = 512
        val TERMINAL_STATES = setOf("TERMINAL", "COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT", "INTERRUPTED", "STOPPED")
    }
}
