package com.slafftrosheen.halomodellab.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slafftrosheen.halomodellab.AppContainer
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HaloBinderGatewayDeviceTest {
    @Test
    fun packageScopedHaloBridgeBindsAndReturnsConsentOrReady() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reply = HaloBinderGateway(context).handshake(
            BridgeHandshakeRequest(
                protocolVersion = BridgeProtocolVersion(1, 1),
                requestId = UUID.randomUUID().toString(),
                clientProcessInstanceId = UUID.randomUUID().toString(),
                clientSourceId = "not_recorded",
                clientSchemaSha256 = AppContainer.LAB_CLIENT_SCHEMA_SHA256,
                requestedFeatures = setOf(BridgeFeature.MODEL_INVENTORY),
            ),
        )

        assertTrue(
            "Expected authenticated Halo AIDL handshake, got ${reply.status}",
            reply.status == BridgeConnectionStatus.ConsentRequired || reply.status == BridgeConnectionStatus.Ready,
        )
        if (reply.status == BridgeConnectionStatus.Ready) {
            assertEquals(AppContainer.HALO_PROTOCOL_SCHEMA_SHA256, reply.serverSchemaSha256)
            assertEquals(1, reply.protocolVersion?.major)
        } else {
            assertTrue(reply.grantedConsent.isEmpty())
        }
    }
}
