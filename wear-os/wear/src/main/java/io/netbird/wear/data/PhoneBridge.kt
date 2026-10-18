package io.netbird.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import io.netbird.wear.model.Command
import io.netbird.wear.model.CommandPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/** Raised when no paired phone exposing the NetBird bridge capability is reachable. */
class PhoneNotReachableException(message: String = "NetBird phone bridge is not reachable") :
    Exception(message)

/**
 * Sends commands to the phone over the Wearable Data Layer.
 *
 * The phone app advertises the `netbird_wear_bridge` capability; we target that
 * node directly. As a fallback (e.g. capability not yet propagated right after
 * installing) we fall back to the first connected node.
 */
class PhoneBridge(context: Context) {
    private val appContext = context.applicationContext
    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(appContext)
    private val nodeClient: NodeClient = Wearable.getNodeClient(appContext)

    suspend fun send(command: Command, route: String? = null): Result<CommandPayload> = withContext(Dispatchers.IO) {
        try {
            val node = findPhoneNode()
            val payload = CommandPayload(
                command = command,
                requestId = UUID.randomUUID().toString(),
                route = route,
            )
            messageClient.sendMessage(node.id, PATH_COMMAND, payload.encode()).await()
            Log.i(TAG, "sent ${command.wire} to node ${node.id}" + (if (route != null) " route=$route" else ""))
            Result.success(payload)
        } catch (t: Throwable) {
            Log.w(TAG, "could not send ${command.wire}", t)
            Result.failure(t)
        }
    }

    private suspend fun findPhoneNode(): Node {
        val capability = capabilityClient
            .getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
        Log.i(TAG, "capability nodes: " + capability.nodes.joinToString { "${it.displayName}/${it.id}" })

        // Diagnostic: is the capability registered at all, or just not reachable?
        val allCapability = capabilityClient
            .getCapability(CAPABILITY, CapabilityClient.FILTER_ALL)
            .await()
        Log.i(TAG, "capability(all) nodes: " + allCapability.nodes.joinToString { "${it.displayName}/${it.id}" })

        capability.nodes.firstOrNull()?.let { return it }

        val connected = nodeClient.connectedNodes.await()
        Log.i(TAG, "connected nodes: " + connected.joinToString { "${it.displayName}/${it.id}" })
        return connected.firstOrNull()
            ?: throw PhoneNotReachableException("no connected or capability node")
    }

    companion object {
        private const val TAG = "NetBirdWearBridge"

        const val CAPABILITY = "netbird_wear_bridge"
        const val PATH_COMMAND = "/netbird/command"
        const val PATH_STATE = "/netbird/state"
    }
}
