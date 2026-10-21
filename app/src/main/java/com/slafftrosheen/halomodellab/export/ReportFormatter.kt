package com.slafftrosheen.halomodellab.export

import com.slafftrosheen.halomodellab.domain.AttemptOutcome
import com.slafftrosheen.halomodellab.domain.EvidenceOrigin
import com.slafftrosheen.halomodellab.domain.FailureDomain
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
    ): String {
        val attempts = detail.queue.flatMap { item -> item.attempts.map { item to it } }
        val countsByOutcome = attempts.groupingBy { it.second.outcome }.eachCount()
        val failures = attempts
            .mapNotNull { (item, attempt) ->
                attempt.failure?.let { failure -> Triple(item, attempt, failure) }
            }
            .groupBy { Triple(it.third.code, it.third.domain, it.second.stage.name) }
            .toSortedMap(
                compareBy<Triple<String, FailureDomain, String>> { it.first }
                    .thenBy { it.second }
                    .thenBy { it.third },
            )

        val text = buildString {
            appendLine("# Halo Model Lab — отчёт сессии")
            appendLine()
            if (detail.summary.origin == EvidenceOrigin.SYNTHETIC) {
                appendLine("> **СИМУЛЯЦИЯ.** Это результат управляемого demo-сценария,")
                appendLine("> а не проверка AI-модели, её скорости или памяти.")
                appendLine("> Реальный inference в этой сборке отсутствует.")
                appendLine()
            }
            appendLine("- Создан: $generatedAtIso")
            appendLine("- Сессия: ${detail.summary.sessionId}")
            appendLine("- Состояние: ${detail.summary.state.name}")
            appendLine("- Origin: ${detail.summary.origin.name}")
            appendLine("- Движок: ${detail.summary.engineId}")
            appendLine("- Профиль: ${detail.summary.profileId}")
            appendLine("- Bridge: NOT_IMPLEMENTED; inferenceRuntime: null")
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
            if (failures.isEmpty()) {
                appendLine("- Нет сохранённых причин отказа.")
            } else {
                failures.forEach { (key, entries) ->
                    val (item, attempt, failure) = entries.first()
                    appendLine(
                        "- `${key.first}` (стадия ${key.third}, ${failure.domain.name}, " +
                            "${failure.confidence.name}): попыток ${entries.size}; пример: " +
                            "пункт ${item.ordinal + 1}, попытка ${attempt.number}, " +
                            "attemptId ${attempt.attemptId}",
                    )
                }
            }
            appendLine()

            appendLine("## Предположено")
            appendLine("- Автоматических выводов о причине не делалось. Повторяемость не доказана.")
            appendLine()
            appendLine("## Не установлено")
            appendLine("- Причина смерти процесса, если она была, остаётся UNKNOWN.")
            appendLine("- Потребление ресурсов моделью не измерялось: настоящей модели нет.")
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
            appendLine("Сопоставимого baseline нет: автоматическое сравнение прогонов не реализовано.")
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
            appendLine("- failures/ — отдельный JSON на attemptId.")
            appendLine("- checksums.sha256 — контрольные суммы payload-файлов.")
        }

        return truncateToUtf8Bytes(text, MAX_REPORT_BYTES)
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

    private fun truncateToUtf8Bytes(text: String, maxBytes: Int): String {
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
        val marker = "\n\n[report truncated: 8 KiB limit]\n"
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
