package com.slafftrosheen.halomodellab.profiles

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFixtureValidatorsTest {
    @Test
    fun invalidAdapterEnvelopeIsUnknownRatherThanModelFailure() {
        val probe = requireNotNull(TextProfileRegistry.resolve("fast-smoke", "1")).cases.single()
        val result = TextFixtureValidators.evaluate(probe, TextProbeObservation(false, "WRONG", emptyList()))
        assertEquals(ProbeVerdict.UNKNOWN, result.verdict)
        assertEquals("ADAPTER_RESPONSE_INVALID", result.reasonCode)
    }

    @Test
    fun exactTextMismatchIsAQualityFailure() {
        val probe = requireNotNull(TextProfileRegistry.resolve("fast-smoke", "1")).cases.single()
        val result = TextFixtureValidators.evaluate(probe, TextProbeObservation(true, "NOT READY", emptyList()))
        assertEquals(ProbeVerdict.FAIL, result.verdict)
        assertEquals("EXACT_TEXT_MISMATCH", result.reasonCode)
    }

    @Test
    fun jsonFieldValidatorDistinguishesMatchMismatchAndInvalidOutput() {
        val probe = requireNotNull(TextProfileRegistry.resolve("chat-standard", "1")).cases.first()
        assertEquals(
            ProbeVerdict.PASS,
            TextFixtureValidators.evaluate(probe, TextProbeObservation(true, "{\"status\":\"ready\"}", emptyList())).verdict,
        )
        assertEquals(
            "JSON_FIELD_MISMATCH",
            TextFixtureValidators.evaluate(probe, TextProbeObservation(true, "{\"status\":\"busy\"}", emptyList())).reasonCode,
        )
        assertEquals(
            "INVALID_JSON_OUTPUT",
            TextFixtureValidators.evaluate(probe, TextProbeObservation(true, "not json", emptyList())).reasonCode,
        )
    }

    @Test
    fun fixtureToolValidatorIsPureAndRejectsWrongNameArgumentsOrMultiplicity() {
        val probe = requireNotNull(TextProfileRegistry.resolve("action-standard", "2")).cases.first()
        val expected = ObservedFixtureToolCall("lookup_fixture", "{\"id\":\"item-1\"}")
        assertEquals(
            ProbeVerdict.PASS,
            TextFixtureValidators.evaluate(probe, TextProbeObservation(true, "", listOf(expected))).verdict,
        )
        assertEquals(
            "FIXTURE_TOOL_CALL_MISMATCH",
            TextFixtureValidators.evaluate(
                probe,
                TextProbeObservation(true, "", listOf(expected.copy(argumentsJson = "{\"id\":\"other\"}"))),
            ).reasonCode,
        )
        assertEquals(
            "FIXTURE_TOOL_CALL_MISMATCH",
            TextFixtureValidators.evaluate(
                probe,
                TextProbeObservation(true, "", listOf(expected.copy(name = "different_fixture"))),
            ).reasonCode,
        )
        assertEquals(
            "TOOL_ARGUMENTS_INVALID",
            TextFixtureValidators.evaluate(
                probe,
                TextProbeObservation(true, "", listOf(expected.copy(argumentsJson = "[]"))),
            ).reasonCode,
        )
        assertEquals(
            "TOOL_CALL_COUNT_MISMATCH",
            TextFixtureValidators.evaluate(probe, TextProbeObservation(true, "", listOf(expected, expected))).reasonCode,
        )
    }

    @Test
    fun noToolAndContinuationCasesKeepUnknownSeparateFromFailure() {
        val noTool = requireNotNull(TextProfileRegistry.resolve("chat-standard", "1")).cases.last()
        assertEquals(
            ProbeVerdict.PASS,
            TextFixtureValidators.evaluate(noTool, TextProbeObservation(true, "blue scattering", emptyList())).verdict,
        )
        assertEquals(
            ProbeVerdict.UNKNOWN,
            TextFixtureValidators.evaluate(noTool, TextProbeObservation(true, "", null)).verdict,
        )
        val continuation = requireNotNull(TextProfileRegistry.resolve("action-standard", "2")).cases.last()
        assertEquals(
            ProbeVerdict.PASS,
            TextFixtureValidators.evaluate(continuation, TextProbeObservation(true, "amber", emptyList())).verdict,
        )
    }
}
