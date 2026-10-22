package com.slafftrosheen.halomodellab.domain

/**
 * Fixed v0 domain contract (HML-SPEC-001, HML-L0).
 *
 * These types are framework-free on purpose: no Android, Room or Moshi annotations belong here.
 * Room entities and export DTOs are separate types added in later packages.
 */

enum class EvidenceOrigin { SYNTHETIC, DEVICE_MEASUREMENT, IMPORTED_UNVERIFIED }

enum class ResultValidity { VALID, INVALID, UNKNOWN }

enum class AttemptOutcome { PASSED, FAILED, TIMED_OUT, CANCELLED, INTERRUPTED, SKIPPED }

enum class TestStage { PREFLIGHT, LOAD, EXECUTE, UNLOAD }

enum class FailureDomain { MODEL, ADAPTER, RUNTIME, DEVICE, ENVIRONMENT, HARNESS, UNKNOWN }

enum class EvidenceConfidence { OBSERVED, INFERRED, UNKNOWN }

enum class ModelRole { FAST, CHAT, THINK, ACTION, VISION, STT, TTS, EMBEDDING }

enum class ComputeBackend { CPU, GPU, NPU, UNSPECIFIED }

data class FailureInfo(
    val code: String,
    val domain: FailureDomain,
    val confidence: EvidenceConfidence,
    val detail: String,
)

data class Metric(
    val name: String,
    val value: Double?,
    val unit: String,
    val scope: String,
    val origin: EvidenceOrigin,
    val unavailableReason: String? = null,
) {
    init {
        require(value == null || value.isFinite())
        require(value != null || !unavailableReason.isNullOrBlank())
    }
}

data class AttemptResult(
    val outcome: AttemptOutcome,
    val validity: ResultValidity,
    val origin: EvidenceOrigin,
    val failure: FailureInfo? = null,
    val metrics: List<Metric> = emptyList(),
) {
    init {
        require(outcome != AttemptOutcome.PASSED || failure == null)
    }
}

data class BenchmarkRequest(
    val sessionId: String,
    val attemptId: String,
    val subjectId: String,
    val caseId: String,
    val caseTimeoutMs: Long,
    val origin: EvidenceOrigin,
    val role: ModelRole,
    val requestedBackend: ComputeBackend,
    val scenarioId: String? = null,
    val stageBudgets: StageBudgets? = null,
) {
    init {
        require(sessionId.isNotBlank() && attemptId.isNotBlank())
        require(subjectId.isNotBlank() && caseId.isNotBlank())
        require(caseTimeoutMs > 0)
    }
}

fun interface AttemptEventSink {
    // Return only after the stage/event transaction has committed.
    // Storage failure must propagate; the runner must stop, not fabricate success.
    suspend fun record(stage: TestStage, code: String)
}

interface BenchmarkEngine {
    val engineId: String
    val origin: EvidenceOrigin

    /** True when the engine owns a deadline that can stop and confirm remote/native cleanup. */
    val managesOwnDeadline: Boolean get() = false

    suspend fun execute(
        request: BenchmarkRequest,
        events: AttemptEventSink,
    ): AttemptResult
}
