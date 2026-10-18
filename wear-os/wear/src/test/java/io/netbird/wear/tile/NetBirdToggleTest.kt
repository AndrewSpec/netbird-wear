package io.netbird.wear.tile

import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import io.netbird.wear.model.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class NetBirdToggleTest {
    private fun toggle(state: ConnectionState) = NetBirdToggle.build(
        state,
        ModifiersBuilders.Clickable.Builder().setId("toggle").build(),
        "NetBird VPN: $state",
    )

    private fun track(state: ConnectionState) =
        toggle(state).contents.single() as LayoutElementBuilders.Box

    @Test fun connectedIsOrangeWithThumbOnRight() {
        val track = track(ConnectionState.CONNECTED)
        assertEquals(0xFFF58431.toInt(), track.modifiers!!.background!!.color!!.argb)
        assertEquals(LayoutElementBuilders.HORIZONTAL_ALIGN_RIGHT, track.horizontalAlignment!!.value)
    }

    @Test fun disconnectedIsGreyWithThumbOnLeft() {
        val track = track(ConnectionState.DISCONNECTED)
        assertEquals(0xFF3A3A3A.toInt(), track.modifiers!!.background!!.color!!.argb)
        assertEquals(LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT, track.horizontalAlignment!!.value)
    }

    @Test fun transitionsFollowConnectionDirection() {
        assertTrue(NetBirdToggle.isOn(ConnectionState.CONNECTING))
        assertFalse(NetBirdToggle.isOn(ConnectionState.DISCONNECTING))
    }

    @Test fun unknownAndErrorStatesNeverPretendConnected() {
        ConnectionState.entries.filter {
            it != ConnectionState.CONNECTED && it != ConnectionState.CONNECTING
        }.forEach { assertFalse(it.name, NetBirdToggle.isOn(it)) }
    }

    @Test fun entireControlIsClickableAndHasAccessibleDescription() {
        val toggle = toggle(ConnectionState.CONNECTED)
        assertEquals("toggle", toggle.modifiers!!.clickable!!.id)
        assertEquals("NetBird VPN: CONNECTED", toggle.modifiers!!.semantics!!.contentDescription)
        val thumb = track(ConnectionState.CONNECTED).contents.single() as LayoutElementBuilders.Box
        assertEquals(0xFFFFFFFF.toInt(), thumb.modifiers!!.background!!.color!!.argb)
        assertNull(thumb.modifiers!!.clickable)
    }
}
