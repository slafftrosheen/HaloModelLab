package com.slafftrosheen.halomodellab.profiles

import com.squareup.moshi.JsonReader
import okio.Buffer

enum class ProbeVerdict { PASS, FAIL, UNKNOWN }

data class ObservedFixtureToolCall(
    val name: String,
    val argumentsJson: String,
)

/** Bounded, decoded adapter facts. Invalid transport payloads must be represented as unknown. */
data class TextProbeObservation(
    val adapterResponseValid: Boolean,
    val text: String?,
    val toolCalls: List<ObservedFixtureToolCall>?,
) {
    init {
        require(text == null || text.length <= MAX_OBSERVED_TEXT_CHARS)
        require(toolCalls == null || toolCalls.size <= MAX_TOOL_CALLS)
        require(toolCalls.orEmpty().all {
            it.name.isNotBlank() && it.name.length <= MAX_TOOL_NAME_CHARS &&
                it.argumentsJson.isNotBlank() && it.argumentsJson.length <= FixtureToolCallContract.MAX_ARGUMENTS_CHARS
        })
    }

    companion object {
        const val MAX_OBSERVED_TEXT_CHARS = 8_192
        const val MAX_TOOL_CALLS = 8
        const val MAX_TOOL_NAME_CHARS = 64
    }
}

data class TextProbeAssertionResult(
    val caseId: String,
    val verdict: ProbeVerdict,
    val reasonCode: String,
)

/** Pure validator: fixture tool calls are compared as data and are never dispatched. */
object TextFixtureValidators {
    fun evaluate(probe: TextProbeCase, observation: TextProbeObservation): TextProbeAssertionResult {
        if (!observation.adapterResponseValid) return result(probe, ProbeVerdict.UNKNOWN, "ADAPTER_RESPONSE_INVALID")
        val verdictAndReason = when (probe.assertion) {
            TextAssertionKind.EXACT_TEXT -> when {
                observation.text == null -> ProbeVerdict.UNKNOWN to "TEXT_UNAVAILABLE"
                observation.text.trim() == probe.expectedValue -> ProbeVerdict.PASS to "EXACT_TEXT_MATCH"
                else -> ProbeVerdict.FAIL to "EXACT_TEXT_MISMATCH"
            }
            TextAssertionKind.JSON_FIELD -> evaluateJsonField(probe, observation.text)
            TextAssertionKind.FIXTURE_TOOL_CALL -> evaluateToolCall(probe, observation.toolCalls)
            TextAssertionKind.NO_TOOL_CALL -> when (val calls = observation.toolCalls) {
                null -> ProbeVerdict.UNKNOWN to "TOOL_CALL_FACTS_UNAVAILABLE"
                emptyList<ObservedFixtureToolCall>() -> ProbeVerdict.PASS to "NO_TOOL_CALL"
                else -> ProbeVerdict.FAIL to "UNEXPECTED_TOOL_CALL"
            }
            TextAssertionKind.CONTINUE_AFTER_TOOL_RESULT -> when {
                observation.text == null -> ProbeVerdict.UNKNOWN to "TEXT_UNAVAILABLE"
                observation.text.trim() == probe.expectedValue -> ProbeVerdict.PASS to "CONTINUATION_MATCH"
                else -> ProbeVerdict.FAIL to "CONTINUATION_MISMATCH"
            }
        }
        return result(probe, verdictAndReason.first, verdictAndReason.second)
    }

    private fun evaluateJsonField(probe: TextProbeCase, text: String?): Pair<ProbeVerdict, String> {
        if (text == null) return ProbeVerdict.UNKNOWN to "TEXT_UNAVAILABLE"
        val expected = probe.expectedValue.split('=', limit = 2)
        if (expected.size != 2 || expected[0].isBlank()) return ProbeVerdict.UNKNOWN to "FIXTURE_CONTRACT_INVALID"
        return try {
            val reader = JsonReader.of(Buffer().writeUtf8(text))
            reader.isLenient = false
            reader.beginObject()
            var field: String? = null
            var found = false
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == expected[0]) {
                    if (found) return ProbeVerdict.FAIL to "JSON_FIELD_DUPLICATED"
                    found = true
                    if (reader.peek() == JsonReader.Token.STRING) field = reader.nextString()
                    else reader.skipValue()
                }
                else reader.skipValue()
            }
            reader.endObject()
            if (reader.peek() != JsonReader.Token.END_DOCUMENT) return ProbeVerdict.FAIL to "JSON_TRAILING_CONTENT"
            if (field == expected[1]) ProbeVerdict.PASS to "JSON_FIELD_MATCH"
            else ProbeVerdict.FAIL to "JSON_FIELD_MISMATCH"
        } catch (_: Exception) {
            ProbeVerdict.FAIL to "INVALID_JSON_OUTPUT"
        }
    }

    private fun evaluateToolCall(
        probe: TextProbeCase,
        calls: List<ObservedFixtureToolCall>?,
    ): Pair<ProbeVerdict, String> {
        val expected = probe.expectedToolCall ?: return ProbeVerdict.UNKNOWN to "FIXTURE_CONTRACT_INVALID"
        if (calls == null) return ProbeVerdict.UNKNOWN to "TOOL_CALL_FACTS_UNAVAILABLE"
        if (calls.size != 1) return ProbeVerdict.FAIL to "TOOL_CALL_COUNT_MISMATCH"
        val actual = calls.single()
        val validArguments = runCatching { validateJsonObject(actual.argumentsJson) }.isSuccess
        if (!validArguments) return ProbeVerdict.FAIL to "TOOL_ARGUMENTS_INVALID"
        return if (actual.name == expected.name && actual.argumentsJson == expected.argumentsJson) {
            ProbeVerdict.PASS to "FIXTURE_TOOL_CALL_MATCH"
        } else ProbeVerdict.FAIL to "FIXTURE_TOOL_CALL_MISMATCH"
    }

    private fun validateJsonObject(value: String) {
        val reader = JsonReader.of(Buffer().writeUtf8(value))
        reader.isLenient = false
        reader.beginObject()
        while (reader.hasNext()) {
            reader.nextName()
            reader.skipValue()
        }
        reader.endObject()
        require(reader.peek() == JsonReader.Token.END_DOCUMENT)
    }

    private fun result(probe: TextProbeCase, verdict: ProbeVerdict, reason: String) =
        TextProbeAssertionResult(probe.caseId, verdict, reason)
}
