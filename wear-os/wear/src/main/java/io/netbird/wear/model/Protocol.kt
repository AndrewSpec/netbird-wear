package io.netbird.wear.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Connection state as understood by the watch UI.
 *
 * The wire names are the contract with the phone-side bridge
 * (`io.netbird.client.wear.WearBridgeService`); keep both sides in sync.
 */
enum class ConnectionState(val wire: String) {
    UNKNOWN("UNKNOWN"),
    DISCONNECTED("DISCONNECTED"),
    CONNECTING("CONNECTING"),
    CONNECTED("CONNECTED"),
    DISCONNECTING("DISCONNECTING"),
    NEEDS_LOGIN("NEEDS_LOGIN"),
    NEEDS_SETUP("NEEDS_SETUP"),
    NO_NETWORK("NO_NETWORK"),
    ERROR("ERROR"),
    PHONE_UNAVAILABLE("PHONE_UNAVAILABLE");

    val isBusy: Boolean
        get() = this == CONNECTING || this == DISCONNECTING

    val isConnected: Boolean
        get() = this == CONNECTED

    companion object {
        private val byWire: Map<String, ConnectionState> = entries.associateBy { it.wire }

        fun fromWire(value: String?): ConnectionState =
            value?.let { byWire[it] } ?: UNKNOWN
    }
}

/** Information about a connected or visible NetBird peer machine. */
data class PeerItem(
    val fqdn: String,
    val ip: String,
    val ipv6: String? = null,
    val status: Long = 0L, // 1 = connected, 0 = disconnected
    val latencyMs: Long = 0L,
) {
    val isConnected: Boolean get() = status == 1L

    fun toJson(): JSONObject = JSONObject().apply {
        put("fqdn", fqdn)
        put("ip", ip)
        ipv6?.let { put("ipv6", it) }
        put("status", status)
        put("latency", latencyMs)
    }

    companion object {
        fun fromJson(json: JSONObject): PeerItem = PeerItem(
            fqdn = json.optString("fqdn", ""),
            ip = json.optString("ip", ""),
            ipv6 = json.optStringOrNull("ipv6"),
            status = json.optLong("status", 0L),
            latencyMs = json.optLong("latency", 0L),
        )
    }
}

/** Routed network / resource available in the NetBird network. */
data class ResourceItem(
    val name: String,
    val network: String,
    val status: String = "",
    val isSelected: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("network", network)
        put("status", status)
        put("selected", isSelected)
    }

    companion object {
        fun fromJson(json: JSONObject): ResourceItem = ResourceItem(
            name = json.optString("name", ""),
            network = json.optString("network", ""),
            status = json.optString("status", ""),
            isSelected = json.optBoolean("selected", false),
        )
    }
}

/**
 * A state snapshot received from the phone bridge, or synthesized locally when
 * the phone cannot be reached.
 */
data class BridgeState(
    val state: ConnectionState = ConnectionState.UNKNOWN,
    val message: String? = null,
    val deviceName: String? = null,
    val requestId: String? = null,
    val timestamp: Long = 0L,
    val fqdn: String? = null,
    val ip: String? = null,
    val peers: List<PeerItem> = emptyList(),
    val resources: List<ResourceItem> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("state", state.wire)
        message?.let { put("message", it) }
        deviceName?.let { put("deviceName", it) }
        requestId?.let { put("requestId", it) }
        put("timestamp", timestamp)
        fqdn?.let { put("fqdn", it) }
        ip?.let { put("ip", it) }

        val peersArr = JSONArray()
        peers.forEach { peersArr.put(it.toJson()) }
        put("peers", peersArr)

        val resArr = JSONArray()
        resources.forEach { resArr.put(it.toJson()) }
        put("resources", resArr)
    }.toString()

    companion object {
        fun fromJson(raw: String): BridgeState = runCatching {
            val json = JSONObject(raw)
            val peersList = mutableListOf<PeerItem>()
            json.optJSONArray("peers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { peersList.add(PeerItem.fromJson(it)) }
                }
            }

            val resourcesList = mutableListOf<ResourceItem>()
            json.optJSONArray("resources")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { resourcesList.add(ResourceItem.fromJson(it)) }
                }
            }

            BridgeState(
                state = ConnectionState.fromWire(json.optString("state")),
                message = json.optStringOrNull("message"),
                deviceName = json.optStringOrNull("deviceName"),
                requestId = json.optStringOrNull("requestId"),
                timestamp = json.optLong("timestamp", 0L),
                fqdn = json.optStringOrNull("fqdn"),
                ip = json.optStringOrNull("ip"),
                peers = peersList,
                resources = resourcesList,
            )
        }.getOrElse {
            BridgeState(state = ConnectionState.ERROR, message = "Malformed state message")
        }
    }
}

/** Commands the watch can send to the phone bridge. */
enum class Command(val wire: String) {
    CONNECT("connect"),
    DISCONNECT("disconnect"),
    TOGGLE("toggle"),
    STATUS("status"),
    SELECT_ROUTE("select_route"),
    DESELECT_ROUTE("deselect_route");

    companion object {
        fun fromWire(value: String?): Command? = entries.firstOrNull { it.wire == value }

        fun forCurrentState(state: ConnectionState): Command = when {
            state.isConnected -> DISCONNECT
            state == ConnectionState.CONNECTING -> DISCONNECT
            state == ConnectionState.DISCONNECTING -> CONNECT
            else -> CONNECT
        }
    }
}

data class CommandPayload(
    val command: Command,
    val requestId: String,
    val route: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("command", command.wire)
        put("requestId", requestId)
        route?.let { put("route", it) }
    }.toString()

    fun encode(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(raw: ByteArray): CommandPayload? = runCatching {
            val json = JSONObject(String(raw, Charsets.UTF_8))
            val command = Command.fromWire(json.optString("command")) ?: return null
            CommandPayload(
                command = command,
                requestId = json.optString("requestId"),
                route = json.optStringOrNull("route"),
            )
        }.getOrNull()
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null
