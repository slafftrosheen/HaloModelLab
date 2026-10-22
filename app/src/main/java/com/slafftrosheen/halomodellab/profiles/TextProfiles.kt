package com.slafftrosheen.halomodellab.profiles

import com.slafftrosheen.halomodellab.domain.ModelRole
import java.security.MessageDigest

enum class TextProbeMode { PREFLIGHT, SMOKE, COLD_PROCESS, WARM_SERIES }
enum class TextAssertionKind { EXACT_TEXT, JSON_FIELD, FIXTURE_TOOL_CALL, NO_TOOL_CALL, CONTINUE_AFTER_TOOL_RESULT }

data class FixtureToolCallContract(
    val name: String,
    val argumentsJson: String,
) {
    init {
        require(name.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")))
        require(argumentsJson.isNotBlank() && argumentsJson.length <= MAX_ARGUMENTS_CHARS)
    }

    companion object { const val MAX_ARGUMENTS_CHARS = 2_048 }
}

data class TextProbeCase(
    val caseId: String,
    val prompt: String,
    val assertion: TextAssertionKind,
    val expectedValue: String,
    val maximumOutputTokens: Int,
    val languageTag: String = "en",
    val expectedToolCall: FixtureToolCallContract? = null,
    val precedingFixtureToolResult: String? = null,
) {
    init {
        require(SAFE_ID.matches(caseId))
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARS)
        require(expectedValue.length <= MAX_EXPECTED_CHARS)
        require(
            when (assertion) {
                TextAssertionKind.NO_TOOL_CALL -> expectedValue.isEmpty() && expectedToolCall == null
                TextAssertionKind.FIXTURE_TOOL_CALL -> expectedValue.isEmpty() && expectedToolCall != null
                else -> expectedValue.isNotBlank() && expectedToolCall == null
            },
        )
        require(precedingFixtureToolResult == null ||
            (assertion == TextAssertionKind.CONTINUE_AFTER_TOOL_RESULT && precedingFixtureToolResult.length <= MAX_EXPECTED_CHARS))
        require(assertion != TextAssertionKind.CONTINUE_AFTER_TOOL_RESULT || !precedingFixtureToolResult.isNullOrBlank())
        require(maximumOutputTokens in 1..MAX_OUTPUT_TOKENS)
        require(LANGUAGE_TAG.matches(languageTag))
    }

    companion object {
        const val MAX_PROMPT_CHARS = 4_096
        const val MAX_EXPECTED_CHARS = 512
        const val MAX_OUTPUT_TOKENS = 4_096
        private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,96}")
        private val LANGUAGE_TAG = Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,3}")
    }
}

data class TextSuiteContract(
    val suiteId: String,
    val suiteVersion: String,
    val role: ModelRole,
    val mode: TextProbeMode,
    val cases: List<TextProbeCase>,
) {
    init {
        require(SAFE_SUITE_ID.matches(suiteId) && SAFE_SUITE_ID.matches(suiteVersion))
        require(cases.isNotEmpty() && cases.size <= MAX_CASES)
        require(cases.map { it.caseId }.distinct().size == cases.size)
        require(role in SUPPORTED_TEXT_ROLES)
    }

    /** Stable fixture identity. Outputs stay observations; no deterministic model output is claimed. */
    fun fixtureManifestSha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun canonicalJson(): String = buildString {
        append('{').append("\"suiteId\":").append(json(suiteId))
        append(",\"suiteVersion\":").append(json(suiteVersion))
        append(",\"role\":").append(json(role.name))
        append(",\"mode\":").append(json(mode.name))
        append(",\"cases\":[")
        cases.sortedBy { it.caseId }.forEachIndexed { index, probe ->
            if (index > 0) append(',')
            append('{').append("\"caseId\":").append(json(probe.caseId))
            append(",\"prompt\":").append(json(probe.prompt))
            append(",\"assertion\":").append(json(probe.assertion.name))
            append(",\"expectedValue\":").append(json(probe.expectedValue))
            append(",\"maximumOutputTokens\":").append(probe.maximumOutputTokens)
            probe.expectedToolCall?.let { call ->
                append(",\"expectedToolCall\":{\"name\":${json(call.name)},\"argumentsJson\":${json(call.argumentsJson)}}")
            }
            probe.precedingFixtureToolResult?.let { append(",\"precedingFixtureToolResult\":").append(json(it)) }
            append(",\"languageTag\":").append(json(probe.languageTag)).append('}')
        }
        append("]}")
    }

    /** Only the single fixed FAST smoke currently has an end-to-end C4 executor. */
    val runnable: Boolean get() = suiteId == "fast-smoke" && suiteVersion == "1" &&
        role == ModelRole.FAST && mode == TextProbeMode.COLD_PROCESS &&
        cases == listOf(TextProbeCase("short-ready", "Reply with exactly READY.", TextAssertionKind.EXACT_TEXT, "READY", 8))

    companion object {
        const val MAX_CASES = 32
        private val SAFE_SUITE_ID = Regex("[A-Za-z0-9._-]{1,96}")
        private val SUPPORTED_TEXT_ROLES = setOf(ModelRole.FAST, ModelRole.CHAT, ModelRole.THINK, ModelRole.ACTION)
    }
}

object TextProfileRegistry {
    private val suites = listOf(
        TextSuiteContract(
            "fast-smoke", "1", ModelRole.FAST, TextProbeMode.COLD_PROCESS,
            listOf(TextProbeCase("short-ready", "Reply with exactly READY.", TextAssertionKind.EXACT_TEXT, "READY", 8)),
        ),
        TextSuiteContract(
            "chat-standard", "1", ModelRole.CHAT, TextProbeMode.COLD_PROCESS,
            listOf(
                TextProbeCase("json-status", "Return one JSON object with the field status set to ready.", TextAssertionKind.JSON_FIELD, "status=ready", 64),
                TextProbeCase("no-tool", "Explain briefly why the sky appears blue. Do not call any tool.", TextAssertionKind.NO_TOOL_CALL, "", 128),
            ),
        ),
        TextSuiteContract(
            "think-standard", "1", ModelRole.THINK, TextProbeMode.COLD_PROCESS,
            listOf(TextProbeCase("arithmetic-17-25", "What is 17 plus 25? Reply with the number only.", TextAssertionKind.EXACT_TEXT, "42", 8)),
        ),
        TextSuiteContract(
            "action-standard", "2", ModelRole.ACTION, TextProbeMode.COLD_PROCESS,
            listOf(
                TextProbeCase("fixture-lookup", "Use lookup_fixture for item-1, then report its label.", TextAssertionKind.FIXTURE_TOOL_CALL, "", 64,
                    expectedToolCall = FixtureToolCallContract("lookup_fixture", "{\"id\":\"item-1\"}")),
                TextProbeCase("fixture-continuation", "Given the fixture result above, reply with the label only.",
                    TextAssertionKind.CONTINUE_AFTER_TOOL_RESULT, "amber", 16,
                    precedingFixtureToolResult = "lookup_fixture({\"id\":\"item-1\"}) -> {\"label\":\"amber\"}"),
            ),
        ),
        TextSuiteContract(
            "fast-warm-series", "1", ModelRole.FAST, TextProbeMode.WARM_SERIES,
            listOf(
                TextProbeCase("warm-first", "Reply with exactly READY.", TextAssertionKind.EXACT_TEXT, "READY", 8),
                TextProbeCase("warm-second", "Reply with exactly SECOND.", TextAssertionKind.EXACT_TEXT, "SECOND", 8),
            ),
        ),
    )

    fun all(): List<TextSuiteContract> = suites
    fun resolve(suiteId: String, version: String): TextSuiteContract? =
        suites.firstOrNull { it.suiteId == suiteId && it.suiteVersion == version }
}

private fun json(value: String): String = buildString {
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
