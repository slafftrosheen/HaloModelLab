package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.SessionDetail

/** Groups repeated current-session observations without discarding their individual attempts. */
object FailureClusterer {
    data class Cluster(
        val code: String,
        val domain: String,
        val stage: String,
        val subjectId: String,
        val role: ModelRole,
        val requestedBackend: ComputeBackend,
        val attemptIds: List<String>,
        val firstObservedWallTimeMs: Long,
        val lastObservedWallTimeMs: Long,
        val exampleAttemptId: String,
        val exampleDetail: String,
    )

    fun cluster(detail: SessionDetail): List<Cluster> = detail.queue
        .flatMap { item -> item.attempts.mapNotNull { attempt ->
            val failure = attempt.failure ?: return@mapNotNull null
            Entry(item.subjectId, item.role, item.requestedBackend, attempt, failure,
                attempt.finishedWallTimeMs ?: attempt.startedWallTimeMs)
        } }
        .groupBy { entry ->
            listOf(entry.failure.code, entry.failure.domain.name, entry.attempt.stage.name,
                entry.subjectId, entry.role.name, entry.requestedBackend.name)
        }
        .toSortedMap(compareBy<List<String>> { it[0] }.thenBy { it[1] }.thenBy { it[2] }
            .thenBy { it[3] }.thenBy { it[4] }.thenBy { it[5] })
        .map { (key, entries) ->
            val ordered = entries.sortedWith(compareBy<Entry> { it.observedAt }.thenBy { it.attempt.attemptId })
            val example = ordered.first()
            Cluster(
                code = key[0],
                domain = key[1],
                stage = key[2],
                subjectId = key[3],
                role = example.role,
                requestedBackend = example.requestedBackend,
                attemptIds = ordered.map { it.attempt.attemptId },
                firstObservedWallTimeMs = ordered.first().observedAt,
                lastObservedWallTimeMs = ordered.last().observedAt,
                exampleAttemptId = example.attempt.attemptId,
                exampleDetail = example.failure.detail.take(MAX_EXAMPLE_DETAIL_CHARS),
            )
        }

    private data class Entry(
        val subjectId: String,
        val role: ModelRole,
        val requestedBackend: ComputeBackend,
        val attempt: AttemptView,
        val failure: FailureInfo,
        val observedAt: Long,
    )

    const val MAX_EXAMPLE_DETAIL_CHARS = 1_024
}
