package io.netbird.wear.tile

import android.content.Context
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.netbird.wear.R
import io.netbird.wear.data.PhoneBridge
import io.netbird.wear.data.StateStore
import io.netbird.wear.model.BridgeState
import io.netbird.wear.model.Command
import io.netbird.wear.model.ConnectionState
import io.netbird.wear.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wear OS Tile for NetBird:
 * - Tapping the toggle switch triggers a headless background connect/disconnect via [ActionBuilders.LoadAction].
 * - Tapping anywhere else on the tile opens [MainActivity].
 */
class NetbirdTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileEnterEvent(event: androidx.wear.tiles.EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(event)
        StateStore.init(applicationContext)
        scope.launch {
            runCatching { PhoneBridge(applicationContext).send(Command.STATUS) }
        }
    }

    override fun onTileAddEvent(event: androidx.wear.tiles.EventBuilders.TileAddEvent) {
        super.onTileAddEvent(event)
        StateStore.init(applicationContext)
        scope.launch {
            runCatching { PhoneBridge(applicationContext).send(Command.STATUS) }
        }
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        StateStore.init(applicationContext)
        val context = applicationContext
        val currentState = StateStore.current()

        // Handle headless toggle when requested from LoadAction
        val lastClickableId = requestParams.currentState?.lastClickableId
            ?: requestParams.state?.lastClickableId

        if (lastClickableId == CLICKABLE_TOGGLE_ID) {
            val nextCommand = Command.forCurrentState(currentState.state)
            // Perform optimistic transition locally so the tile updates immediately upon reload
            val optimisticState = when (nextCommand) {
                Command.CONNECT -> ConnectionState.CONNECTING
                Command.DISCONNECT -> ConnectionState.DISCONNECTING
                else -> currentState.state
            }
            StateStore.update(BridgeState(state = optimisticState, deviceName = currentState.deviceName))

            // Dispatch command in background to phone
            scope.launch {
                runCatching {
                    PhoneBridge(context).send(nextCommand)
                }
            }
        }

        val state = StateStore.current().state

        // 1. General tile click: opens MainActivity
        val appActivity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName(MainActivity::class.java.name)
            .build()

        val openAppAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(appActivity)
            .build()

        val openAppClickable = ModifiersBuilders.Clickable.Builder()
            .setId(CLICKABLE_OPEN_APP_ID)
            .setOnClick(openAppAction)
            .build()

        // 2. Toggle click: triggers LoadAction (headless, re-invokes onTileRequest without opening Activity)
        val toggleLoadAction = ActionBuilders.LoadAction.Builder().build()
        val toggleClickable = ModifiersBuilders.Clickable.Builder()
            .setId(CLICKABLE_TOGGLE_ID)
            .setOnClick(toggleLoadAction)
            .build()

        val actionLabel = context.getString(
            if (NetBirdToggle.isOn(state)) R.string.action_disconnect else R.string.action_connect,
        )
        val toggleButton = NetBirdToggle.build(
            state,
            toggleClickable,
            "NetBird VPN: ${context.getString(labelFor(state))}. $actionLabel",
        )

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.dp(160f))
            .setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId("netbird_logo")
                    .setWidth(DimensionBuilders.dp(32f))
                    .setHeight(DimensionBuilders.dp(32f))
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(context.getString(R.string.app_name))
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(15f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(context.getString(labelFor(state)))
                    .setMaxLines(2)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(12f))
                            .setColor(
                                ColorBuilders.argb(
                                    if (NetBirdToggle.isOn(state)) 0xFFF58431.toInt()
                                    else 0xFFB8BDC5.toInt(),
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(6f))
                    .build(),
            )
            .addContent(toggleButton)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(actionLabel)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(11f))
                            .setColor(ColorBuilders.argb(0xFFB8BDC5.toInt()))
                            .build(),
                    )
                    .build(),
            )
            .build()

        // Background container fills tile and intercepts taps outside the toggle to open app
        val layout = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openAppClickable)
                    .build(),
            )
            .addContent(column)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .setFreshnessIntervalMillis(30_000L)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(
                "netbird_logo",
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.ic_netbird_tile)
                            .build(),
                    )
                    .build(),
            )
            .build()
        return Futures.immediateFuture(resources)
    }

    private fun labelFor(state: ConnectionState): Int = when (state) {
        ConnectionState.CONNECTED -> R.string.status_connected
        ConnectionState.CONNECTING -> R.string.status_connecting
        ConnectionState.DISCONNECTING -> R.string.status_disconnecting
        ConnectionState.NEEDS_LOGIN -> R.string.status_needs_login
        ConnectionState.NEEDS_SETUP -> R.string.status_needs_setup
        ConnectionState.NO_NETWORK -> R.string.status_no_network
        ConnectionState.ERROR -> R.string.status_error
        ConnectionState.PHONE_UNAVAILABLE -> R.string.status_phone_unavailable
        ConnectionState.DISCONNECTED -> R.string.status_disconnected
        ConnectionState.UNKNOWN -> R.string.status_unknown
    }

    companion object {
        private const val RESOURCES_VERSION = "4-netbird-headless"
        const val CLICKABLE_TOGGLE_ID = "toggle_switch"
        const val CLICKABLE_OPEN_APP_ID = "open_app"
    }
}
