package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.AttemptView
import com.slafftrosheen.halomodellab.domain.ComputeBackend
import com.slafftrosheen.halomodellab.domain.EvidenceConfidence
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
import com.slafftrosheen.halomodellab.domain.FailureInfo
import com.slafftrosheen.halomodellab.domain.ModelRole
import com.slafftrosheen.halomodellab.domain.QueueItemState
import com.slafftrosheen.halomodellab.domain.QueueItemView
import com.slafftrosheen.halomodellab.domain.ResultValidity
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionState
import com.slafftrosheen.halomodellab.domain.SessionSummary
import com.slafftrosheen.halomodellab.domain.TestStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFormatterTest {

    @Test
    fun syntheticReportLeadsWithSimulationWarning() {
        val report = ReportFormatter.format(
            detail = detail(origin = EvidenceOrigin.SYNTHETIC),
            generatedAtIso = "2026-10-21T10:00:00Z",
            omissions = emptyList(),
        )

        assertTrue(report.contains("СИМУЛЯЦИЯ"))
        assertTrue(report.contains("comparison.json"))
        assertTrue(report.contains("Bridge/runtime: не использовались"))
        assertTrue(report.contains("Origin: SYNTHETIC"))
        assertTrue(report.contains("Профиль: demo-profile"))
        assertTrue(report.toByteArray(Charsets.UTF_8).size <= ReportFormatter.MAX_REPORT_BYTES)
    }

    @Test
    fun deviceMeasurementReportIsNotLabeledSimulation() {
        val report = ReportFormatter.format(
            detail = detail(origin = EvidenceOrigin.DEVICE_MEASUREMENT),
            generatedAtIso = "2026-10-21T10:00:00Z",
            omissions = emptyList(),
        )

        assertFalse(report.contains("СИМУЛЯЦИЯ"))
        assertTrue(report.contains("Origin: DEVICE_MEASUREMENT"))
    }

    @Test
    fun repeatedFailureIsCountedAndLinkedToAttemptId() {
        val failure = FailureInfo(
            code = "SIMULATED_ADAPTER_LOAD_FAILURE",
            domain = FailureDomain.ADAPTER,
            confidence = EvidenceConfidence.OBSERVED,
            detail = "Synthetic adapter refused to load.",
        )
        val first = attempt(number = 1, failure = failure, repeatIndex = 0)
        val second = attempt(number = 2, failure = failure, repeatIndex = 1)

        val report = ReportFormatter.format(
            detail = detail(
                origin = EvidenceOrigin.SYNTHETIC,
                items = listOf(first, second),
                state = SessionState.COMPLETED,
            ),
            generatedAtIso = "2026-10-21T10:00:00Z",
            omissions = emptyList(),
        )

        assertTrue(report.contains("SIMULATED_ADAPTER_LOAD_FAILURE"))
        assertTrue(report.contains("наблюдений 2"))
        assertTrue(report.contains("стадия EXECUTE"))
        assertTrue(report.contains("Сохранённые стадии: EXECUTE=2"))
        assertTrue(report.contains(first.attempts.first().attemptId))
        assertTrue(report.contains("FAILED: 2"))
        // The report must not turn an observation into a verified root cause.
        assertTrue(report.contains("Автоматических выводов о причине не делалось"))
    }

    @Test
    fun failureClustersKeepAttemptLinksAndBoundExampleDetails() {
        val failure = FailureInfo("MODEL_OUTPUT_MISMATCH", FailureDomain.MODEL, EvidenceConfidence.OBSERVED, "x".repeat(2_000))
        val detail = detail(
            origin = EvidenceOrigin.DEVICE_MEASUREMENT,
            items = listOf(
                attempt(1, failure, 0),
                attempt(2, failure, 1),
            ),
        )

        val cluster = FailureClusterer.cluster(detail).single()

        assertEquals(2, cluster.attemptIds.size)
        assertEquals(1_024, cluster.exampleDetail.length)
        assertEquals(cluster.attemptIds.first(), cluster.exampleAttemptId)
    }

    @Test
    fun reportIsTruncatedToLimitAndSaysSo() {
        val items = (0 until 400).map { index ->
            attempt(
                number = index + 1,
                failure = FailureInfo(
                    code = "SYNTHETIC_FAILURE_$index",
                    domain = FailureDomain.RUNTIME,
                    confidence = EvidenceConfidence.UNKNOWN,
                    detail = "Observed only.",
                ),
                repeatIndex = index,
            )
        }

        val report = ReportFormatter.format(
            detail = detail(
                origin = EvidenceOrigin.SYNTHETIC,
                items = items,
                state = SessionState.COMPLETED,
                repeatCount = 400,
            ),
            generatedAtIso = "2026-10-21T10:00:00Z",
            omissions = listOf("events.jsonl truncated: included 10 of 20."),
        )

        assertTrue(report.toByteArray(Charsets.UTF_8).size <= ReportFormatter.MAX_REPORT_BYTES)
        assertTrue(report.contains("[report truncated"))
        // Omissions that the exporter recorded must survive the 8 KiB cut.
        assertFalse(report.contains("## Файлы"))
    }

    @Test
    fun omissionsAreListedWhenTheyFit() {
        val report = ReportFormatter.format(
            detail = detail(origin = EvidenceOrigin.SYNTHETIC),
            generatedAtIso = "2026-10-21T10:00:00Z",
            omissions = listOf("telemetry.csv truncated: included 5000 of 6000 samples."),
        )

        assertTrue(report.contains("## Omissions"))
        assertTrue(report.contains("5000 of 6000"))
    }

    @Test
    fun missingMetricsAreNotReportedAsZero() {
        val report = ReportFormatter.format(
            detail = detail(origin = EvidenceOrigin.SYNTHETIC),
            generatedAtIso = "2026-10-21T10:00:00Z",
            omissions = emptyList(),
        )

        assertTrue(report.contains("Потребление ресурсов моделью не измерялось"))
        assertEquals(false, report.contains("0 MB"))
    }

    private data class FailureItem(
        val repeatIndex: Int,
        val attempts: List<AttemptView>,
    )

    private fun attempt(
        number: Int,
        failure: FailureInfo?,
        repeatIndex: Int,
    ): FailureItem {
        val attemptId = "00000000-0000-4000-8000-%012d".format(repeatIndex + number)
        return FailureItem(
            repeatIndex = repeatIndex,
            attempts = listOf(
                AttemptView(
                    attemptId = attemptId,
                    number = number,
                    stage = TestStage.EXECUTE,
                    outcome = if (failure == null) AttemptOutcome.PASSED else AttemptOutcome.FAILED,
                    validity = ResultValidity.VALID,
                    failure = failure,
                    deadlineReason = null,
                    ownerInstanceId = "owner-1",
                    startedWallTimeMs = 1_790_000_000_000L,
                    finishedWallTimeMs = 1_790_000_001_000L,
                    elapsedMs = 1_000L,
                ),
            ),
        )
    }

    private fun detail(
        origin: EvidenceOrigin,
        items: List<FailureItem> = listOf(attempt(number = 1, failure = null, repeatIndex = 0)),
        state: SessionState = SessionState.COMPLETED,
        repeatCount: Int = 1,
    ): SessionDetail {
        val queue = items.mapIndexed { index, item ->
            QueueItemView(
                queueItemId = "item-$index",
                ordinal = index,
                subjectId = "demo/success",
                scenarioId = "demo/success",
                state = QueueItemState.DONE,
                repeatIndex = item.repeatIndex,
                role = ModelRole.CHAT,
                requestedBackend = ComputeBackend.CPU,
                attempts = item.attempts,
            )
        }
        return SessionDetail(
            summary = SessionSummary(
                sessionId = "11111111-2222-4333-8444-555555555555",
                state = state,
                origin = origin,
                engineId = "demo-engine",
                profileId = "demo-profile",
                createdAtWallTimeMs = 1_790_000_000_000L,
                finishedWallTimeMs = 1_790_000_002_000L,
                itemCount = queue.size,
                completedItemCount = queue.size,
            ),
            queue = queue,
            events = emptyList(),
            telemetry = emptyList(),
            caseTimeoutMs = 10_000L,
            budgetMs = 300_000L,
            repeatCount = repeatCount,
            requireCharging = false,
            persistedActiveMs = 2_000L,
            sessionFailure = null,
            planJson = """{"profileId":"demo-profile"}""",
            openAttemptId = null,
        )
    }
}
