package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.SessionDetail
import com.slafftrosheen.halomodellab.domain.SessionState

/**
 * Compact human-readable report, capped at 8 KiB.
 *
 * Observations and inferred causes are kept apart: nothing here guesses a root cause.
 * Simulation is stated in the first lines and cannot be re-labeled by the exporter.
 */
object ReportFormatter {

    const val MAX_REPORT_BYTES: Int = 8 * 1024

    fun format(
        detail: SessionDetail,
        generatedAtIso: String,
        omissions: List<String>,
        comparisonSummary: String = "No previous session selected; comparison was not requested.",
    ): String = bound(formatContent(detail, generatedAtIso, omissions, comparisonSummary))

    internal fun formatContent(
        detail: SessionDetail,
        generatedAtIso: String,
        omissions: List<String>,
        comparisonSummary: String = "No previous session selected; comparison was not requested.",
    ): String {
        val attempts = detail.queue.flatMap { item -> item.attempts.map { item to it } }
        val countsByOutcome = attempts.groupingBy { it.second.outcome }.eachCount()
        val text = buildString {
            appendLine("# Halo Model Lab — отчёт сессии")
            appendLine()
            if (detail.summary.origin == EvidenceOrigin.SYNTHETIC) {
                appendLine("> **СИМУЛЯЦИЯ.** Это результат управляемого demo-сценария,")
                appendLine("> а не проверка AI-модели, её скорости или памяти.")
                appendLine()
            }
            appendLine("- Создан: $generatedAtIso")
            appendLine("- Сессия: ${detail.summary.sessionId}")
            appendLine("- Состояние: ${detail.summary.state.name}")
            appendLine("- Origin: ${detail.summary.origin.name}")
            appendLine("- Движок: ${detail.summary.engineId}")
            appendLine("- Профиль: ${detail.summary.profileId}")
            if (detail.summary.origin == EvidenceOrigin.SYNTHETIC) {
                appendLine("- Bridge/runtime: не использовались в synthetic-сессии.")
            } else if (detail.summary.origin == EvidenceOrigin.DEVICE_MEASUREMENT) {
                appendLine("- Bridge/runtime: см. real-plan.json и remote-events.jsonl; неизвестные значения не восстановлены.")
            } else {
                appendLine("- Bridge/runtime: imported evidence is unverified; see the archived source records.")
            }
            appendLine("- План: повторов ${detail.repeatCount}, таймаут ${detail.caseTimeoutMs} мс, " +
                "бюджет ${detail.budgetMs} мс, зарядка ${if (detail.requireCharging) "требуется" else "не требуется"}")
            appendLine()

            appendLine("## Итог очереди")
            appendLine(
                "Пунктов: ${detail.summary.itemCount}, обработано: " +
                    "${detail.summary.completedItemCount}. Очередь обработана не значит, что все проверки успешны.",
            )
            appendLine(outcomeLine(countsByOutcome))
            appendLine()

            appendLine("## Наблюдалось")
            appendLine("- Попыток сохранено: ${attempts.size}")
            appendLine(
                "- Сохранённые стадии: " + attempts
                    .groupingBy { it.second.stage.name }
                    .eachCount()
                    .toSortedMap()
                    .entries
                    .joinToString { (stage, count) -> "$stage=$count" },
            )
            appendLine("- Событий сохранено: ${detail.events.size}")
            appendLine("- Телеметрия: ${detail.telemetry.size} сэмплов (scope device/lab_process)")
            if (detail.summary.state == SessionState.INTERRUPTED) {
                appendLine("- Сессия прервана потерей процесса; причина не установлена.")
            }
            appendLine()

            appendLine("## Уникальные ошибки")
            val clusters = FailureClusterer.cluster(detail)
            if (clusters.isEmpty()) {
                appendLine("- Нет сохранённых причин отказа.")
            } else {
                clusters.forEach { cluster ->
                    appendLine(
                        "- `${cluster.code}` (стадия ${cluster.stage}, ${cluster.domain}): " +
                            "наблюдений ${cluster.attemptIds.size}; attempt IDs: " +
                            cluster.attemptIds.take(8).joinToString().ifEmpty { "none" } +
                            "; scope ${cluster.subjectId}/${cluster.role}/${cluster.requestedBackend}; " +
                            "пример ${cluster.exampleAttemptId}: ${cluster.exampleDetail.replace(Regex("\\s+"), " ").take(180)}",
                    )
                }
                if (clusters.any { it.attemptIds.size > 8 }) {
                    appendLine("- Полный список attempt IDs и ограниченный пример находятся в failures/clusters.json.")
                }
            }
            appendLine()

            appendLine("## Предположено")
            appendLine("- Автоматических выводов о причине не делалось. Повторяемость не доказана.")
            appendLine()
            appendLine("## Не установлено")
            appendLine("- Причина смерти процесса, если она была, остаётся UNKNOWN.")
            when (detail.summary.origin) {
                EvidenceOrigin.SYNTHETIC -> appendLine("- Потребление ресурсов моделью не измерялось: synthetic-сценарий не запускает модель.")
                EvidenceOrigin.DEVICE_MEASUREMENT -> appendLine("- Отсутствующие resource-метрики остаются unknown; см. telemetry.csv и причины отсутствия.")
                EvidenceOrigin.IMPORTED_UNVERIFIED -> appendLine("- Импортированные resource-метрики не подтверждены текущим устройством.")
            }
            appendLine()

            appendLine("## Воспроизведение")
            appendLine("- Профиль ${detail.summary.profileId}, движок ${detail.summary.engineId}.")
            appendLine(
                "- Сценарии: " + detail.queue.mapNotNull { it.scenarioId }.distinct().joinToString(", "),
            )
            appendLine(
                "- Параметры: повторов ${detail.repeatCount}, таймаут ${detail.caseTimeoutMs} мс, " +
                    "бюджет ${detail.budgetMs} мс.",
            )
            appendLine()

            appendLine("## Baseline")
            appendLine(comparisonSummary)
            appendLine("Выбранная сессия и полные изменения указаны в comparison.json; этот отчёт не называет изменения регрессией.")
            appendLine()

            if (omissions.isNotEmpty()) {
                appendLine("## Omissions")
                omissions.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("## Файлы")
            appendLine("- results.json — план, очередь, попытки, причины.")
            appendLine("- events.jsonl — сохранённые события по seq.")
            appendLine("- telemetry.csv — сэмплы контроллера.")
            appendLine("- coverage.json, qualification.json, comparison.json — coverage и verdict availability.")
            appendLine("- failures/ — отдельные attempt records, index.json и clusters.json; группировка не удаляет observations.")
            appendLine("- checksums.sha256 — контрольные суммы payload-файлов.")
        }

        return text
    }

    private fun outcomeLine(counts: Map<AttemptOutcome?, Int>): String {
        val labels = listOf(
            AttemptOutcome.PASSED,
            AttemptOutcome.FAILED,
            AttemptOutcome.TIMED_OUT,
            AttemptOutcome.CANCELLED,
            AttemptOutcome.INTERRUPTED,
            AttemptOutcome.SKIPPED,
            null,
        )
        return labels.joinToString(", ") { outcome ->
            "${outcome?.name ?: "NO_RESULT"}: ${counts[outcome] ?: 0}"
        }
    }

    internal fun bound(text: String, maxBytes: Int = MAX_REPORT_BYTES): String =
        truncateToUtf8Bytes(text, maxBytes)

    private fun truncateToUtf8Bytes(text: String, maxBytes: Int): String {
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
        val marker = "\n\n[report truncated to fit the export byte limit]\n"
        val budget = maxBytes - marker.toByteArray(Charsets.UTF_8).size
        var low = 0
        var high = text.length
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (text.substring(0, middle).toByteArray(Charsets.UTF_8).size <= budget) {
                low = middle
            } else {
                high = middle - 1
            }
        }
        return text.substring(0, low) + marker
    }
}
