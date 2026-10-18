package io.netbird.wear.service

import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import io.netbird.wear.data.PhoneBridge
import io.netbird.wear.data.StateStore
import io.netbird.wear.model.BridgeState
import io.netbird.wear.model.Command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives state updates pushed by the phone bridge and keeps the tile/UI in
 * sync even when the watch app itself is not in the foreground.
 */
class WearMessageService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PhoneBridge.PATH_STATE) return
        val state = BridgeState.fromJson(String(messageEvent.data, Charsets.UTF_8))
        Log.i(TAG, "state received: ${state.state.wire}")
        StateStore.init(applicationContext)
        StateStore.update(state)
    }

    private companion object {
        private const val TAG = "NetBirdWearBridge"
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != PhoneBridge.CAPABILITY) return
        if (capabilityInfo.nodes.isEmpty()) return
        // The phone bridge just became reachable: refresh whatever the tile
        // shows so it is not stale after the phone app was reinstalled.
        StateStore.init(applicationContext)
        scope.launch {
            PhoneBridge(applicationContext).send(Command.STATUS)
        }
    }
}
