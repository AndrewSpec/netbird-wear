package io.netbird.wear.data

import android.content.Context
import io.netbird.wear.model.BridgeState
import io.netbird.wear.model.ConnectionState
import io.netbird.wear.tile.NetbirdTileService
import androidx.wear.tiles.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the last known bridge state in memory and on disk.
 *
 * Persisted because a state pushed while the UI is closed must still be shown
 * the next time the app (or the tile) starts.
 */
object StateStore {
    private const val PREFS_NAME = "netbird_wear_state"
    private const val KEY_STATE = "state_json"

    private var appContext: Context? = null
    private val _state = MutableStateFlow(BridgeState())

    val state: StateFlow<BridgeState> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        runCatching {
            val raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STATE, null)
            if (raw != null) {
                _state.value = BridgeState.fromJson(raw)
            }
        }
    }

    fun current(): BridgeState = _state.value

    fun update(newState: BridgeState) {
        val ctx = appContext ?: return
        val merged = newState.copy(
            timestamp = if (newState.timestamp > 0L) newState.timestamp else System.currentTimeMillis(),
        )
        _state.value = merged
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, merged.toJson())
                .apply()
        }
        requestTileUpdate(ctx)
    }

    /** The phone could not be reached, or no bridge app is installed there. */
    fun markPhoneUnavailable() {
        val ctx = appContext ?: return
        val next = _state.value.copy(
            state = ConnectionState.PHONE_UNAVAILABLE,
            message = null,
            timestamp = System.currentTimeMillis(),
        )
        _state.value = next
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, next.toJson())
                .apply()
        }
        requestTileUpdate(ctx)
    }

    private fun requestTileUpdate(context: Context) {
        runCatching {
            TileService.getUpdater(context).requestUpdate(NetbirdTileService::class.java)
        }
    }
}
