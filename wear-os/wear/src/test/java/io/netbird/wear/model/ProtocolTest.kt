package io.netbird.wear.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun `bridge state json round trip with ip peers and resources`() {
        val original = BridgeState(
            state = ConnectionState.CONNECTED,
            message = "Tunnel up",
            deviceName = "pixel-8",
            requestId = "req-42",
            timestamp = 1_700_000_000_000L,
            fqdn = "mypc.netbird.cloud",
            ip = "100.64.0.5\nfd00::5",
            peers = listOf(
                PeerItem(fqdn = "srv1.netbird.cloud", ip = "100.64.0.10", status = 1L, latencyMs = 24L),
                PeerItem(fqdn = "srv2.netbird.cloud", ip = "100.64.0.11", status = 0L, latencyMs = 0L),
            ),
            resources = listOf(
                ResourceItem(name = "office-lan", network = "192.168.1.0/24", status = "connected", isSelected = true),
                ResourceItem(name = "cloud-vpc", network = "10.0.0.0/16", status = "disconnected", isSelected = false),
            ),
        )

        val decoded = BridgeState.fromJson(original.toJson())

        assertEquals(original, decoded)
    }

    @Test
    fun `bridge state json omits missing optional fields`() {
        val decoded = BridgeState.fromJson("""{"state":"DISCONNECTED"}""")

        assertEquals(ConnectionState.DISCONNECTED, decoded.state)
        assertNull(decoded.message)
        assertNull(decoded.deviceName)
        assertNull(decoded.requestId)
        assertNull(decoded.fqdn)
        assertNull(decoded.ip)
        assertTrue(decoded.peers.isEmpty())
        assertTrue(decoded.resources.isEmpty())
        assertEquals(0L, decoded.timestamp)
    }

    @Test
    fun `unknown wire state falls back to unknown`() {
        assertEquals(ConnectionState.UNKNOWN, BridgeState.fromJson("""{"state":"TELEPORTING"}""").state)
        assertEquals(ConnectionState.UNKNOWN, ConnectionState.fromWire(null))
    }

    @Test
    fun `malformed payload becomes error state`() {
        val decoded = BridgeState.fromJson("not json at all")

        assertEquals(ConnectionState.ERROR, decoded.state)
        assertTrue(decoded.message!!.isNotEmpty())
    }

    @Test
    fun `command payload round trip with route`() {
        val payload = CommandPayload(Command.SELECT_ROUTE, "abc-123", route = "office-lan")

        val decoded = CommandPayload.decode(payload.encode())

        assertEquals(payload, decoded)
        assertEquals("office-lan", decoded?.route)
    }

    @Test
    fun `unknown command is rejected`() {
        val raw = """{"command":"selfdestruct","requestId":"x"}""".toByteArray()
        assertNull(CommandPayload.decode(raw))
    }

    @Test
    fun `current state maps to the opposite toggle command`() {
        assertEquals(Command.CONNECT, Command.forCurrentState(ConnectionState.DISCONNECTED))
        assertEquals(Command.CONNECT, Command.forCurrentState(ConnectionState.UNKNOWN))
        assertEquals(Command.CONNECT, Command.forCurrentState(ConnectionState.PHONE_UNAVAILABLE))
        assertEquals(Command.DISCONNECT, Command.forCurrentState(ConnectionState.CONNECTED))
        assertEquals(Command.DISCONNECT, Command.forCurrentState(ConnectionState.CONNECTING))
        assertEquals(Command.CONNECT, Command.forCurrentState(ConnectionState.DISCONNECTING))
    }

    @Test
    fun `busy and connected flags are derived correctly`() {
        assertTrue(ConnectionState.CONNECTING.isBusy)
        assertTrue(ConnectionState.DISCONNECTING.isBusy)
        assertTrue(ConnectionState.CONNECTED.isConnected)
        assertFalse(ConnectionState.CONNECTED.isBusy)
        assertFalse(ConnectionState.DISCONNECTED.isBusy)
        assertFalse(ConnectionState.DISCONNECTED.isConnected)
    }
}
