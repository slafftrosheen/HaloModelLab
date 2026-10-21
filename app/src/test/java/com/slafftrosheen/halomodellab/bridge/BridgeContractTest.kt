package com.slafftrosheen.halomodellab.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeContractTest {

    @Test
    fun requestPayloadIsRejectedAboveTheLabLimit() {
        BridgeLimits.requireRequestSize(ByteArray(BridgeLimits.MAX_REQUEST_BYTES))
        assertThrows(BridgePayloadLimitException::class.java) {
            BridgeLimits.requireRequestSize(ByteArray(BridgeLimits.MAX_REQUEST_BYTES + 1))
        }
    }

    @Test
    fun responseEventPagesEnforceCountAndByteBudgets() {
        BridgeLimits.requireEventPage(List(BridgeLimits.MAX_EVENTS_PER_PAGE) { ByteArray(1) })
        assertThrows(BridgePayloadLimitException::class.java) {
            BridgeLimits.requireEventPage(List(BridgeLimits.MAX_EVENTS_PER_PAGE + 1) { ByteArray(1) })
        }
        assertThrows(BridgePayloadLimitException::class.java) {
            BridgeLimits.requireEventPage(listOf(ByteArray(BridgeLimits.MAX_EVENT_PAYLOAD_BYTES + 1)))
        }
    }

    @Test
    fun protocolNegotiationRejectsMajorMismatchAndCapsMinor() {
        assertEquals(
            ProtocolNegotiation.ProtocolMismatch,
            BridgeProtocolVersion(2, 0).negotiate(BridgeProtocolVersion(1, 4)),
        )
        val negotiated = BridgeProtocolVersion(1, 4).negotiate(BridgeProtocolVersion(1, 2))
        assertEquals(ProtocolNegotiation.Compatible(BridgeProtocolVersion(1, 2)), negotiated)
    }

    @Test
    fun unknownConnectionCodeRemainsUnsupported() {
        assertEquals(
            BridgeConnectionStatus.Unsupported("FUTURE_STATE"),
            BridgeConnectionStatus.decode("FUTURE_STATE"),
        )
        assertEquals(BridgeConnectionStatus.Ready, BridgeConnectionStatus.decode("READY"))
    }
}
