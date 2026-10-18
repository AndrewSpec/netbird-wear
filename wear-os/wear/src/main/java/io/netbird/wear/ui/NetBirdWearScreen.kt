package io.netbird.wear.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import io.netbird.wear.R
import io.netbird.wear.data.PhoneBridge
import io.netbird.wear.data.StateStore
import io.netbird.wear.model.BridgeState
import io.netbird.wear.model.Command
import io.netbird.wear.model.CommandPayload
import io.netbird.wear.model.ConnectionState
import io.netbird.wear.model.PeerItem
import io.netbird.wear.model.ResourceItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Background = Color(0xFF1C1E21)
private val Surface = Color(0xFF262A30)
private val Muted = Color(0xFF9AA6B2)
private val NetBirdOrange = Color(0xFFF58431)
private val Green = Color(0xFF4ADE80)
private val Red = Color(0xFFFF5252)
private val Amber = Color(0xFFFFC107)
private val Grey = Color(0xFF7C8794)

private const val REPLY_TIMEOUT_MS = 15_000L

enum class Screen {
    HOME, PEERS, RESOURCES
}

@Composable
fun NetBirdWearApp(
    initialCommand: Command? = null,
    demoMode: Boolean = false,
    demoScreen: String = "HOME",
) {
    val context = LocalContext.current
    val bridge = remember(context) { PhoneBridge(context) }
    val scope = rememberCoroutineScope()
    val liveState by StateStore.state.collectAsStateWithLifecycle()
    
    // Anonymized mock state for documentation screenshots
    val state = remember(demoMode, liveState) {
        if (demoMode) {
            BridgeState(
                state = ConnectionState.CONNECTED,
                deviceName = "Android Phone",
                fqdn = "demo-watch.netbird.cloud",
                ip = "100.64.0.5",
                peers = listOf(
                    PeerItem(fqdn = "gateway.netbird.cloud", ip = "100.64.0.1", status = 1L, latencyMs = 12L),
                    PeerItem(fqdn = "storage-srv.netbird.cloud", ip = "100.64.0.14", status = 1L, latencyMs = 28L),
                    PeerItem(fqdn = "backup-node.netbird.cloud", ip = "100.64.0.22", status = 0L, latencyMs = 0L),
                ),
                resources = listOf(
                    ResourceItem(name = "Corporate LAN", network = "192.168.1.0/24", status = "connected", isSelected = true),
                    ResourceItem(name = "Cloud VPC", network = "10.0.0.0/16", status = "connected", isSelected = true),
                    ResourceItem(name = "Dev Cluster", network = "172.16.0.0/16", status = "disconnected", isSelected = false),
                )
            )
        } else {
            liveState
        }
    }
    
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember(demoScreen) {
        mutableStateOf(
            when (demoScreen.uppercase()) {
                "PEERS" -> Screen.PEERS
                "RESOURCES" -> Screen.RESOURCES
                else -> Screen.HOME
            }
        )
    }

    // System back button & gesture handling
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = Screen.HOME
    }

    suspend fun awaitReply(payload: CommandPayload, before: BridgeState): Boolean {
        val deadline = System.currentTimeMillis() + REPLY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val latest = StateStore.current()
            if (latest.requestId == payload.requestId || latest != before) return true
            delay(200L)
        }
        return false
    }

    fun failureNote(error: Throwable?): String = context.getString(
        if (error is io.netbird.wear.data.PhoneNotReachableException) {
            R.string.label_no_phone_node
        } else {
            R.string.label_send_failed
        },
    )

    suspend fun requestStatus() {
        val before = StateStore.current()
        val result = bridge.send(Command.STATUS)
        val payload = result.getOrNull()
        if (payload == null) {
            StateStore.markPhoneUnavailable()
            note = failureNote(result.exceptionOrNull())
            return
        }
        if (!awaitReply(payload, before)) {
            StateStore.markPhoneUnavailable()
        }
    }

    suspend fun runCommand(command: Command, route: String? = null) {
        if (busy) return
        busy = true
        note = null
        val before = StateStore.current()
        val result = bridge.send(command, route)
        val payload = result.getOrNull()
        if (payload == null) {
            StateStore.markPhoneUnavailable()
            note = failureNote(result.exceptionOrNull())
            busy = false
            return
        }
        if (!awaitReply(payload, before)) {
            StateStore.markPhoneUnavailable()
            note = context.getString(R.string.label_no_response)
        }
        busy = false
    }

    LifecycleResumeEffect(Unit) {
        scope.launch { requestStatus() }
        onPauseOrDispose { }
    }

    LaunchedEffect(initialCommand) {
        if (initialCommand != null) {
            when (initialCommand) {
                Command.TOGGLE -> runCommand(Command.forCurrentState(StateStore.current().state))
                Command.CONNECT, Command.DISCONNECT -> runCommand(initialCommand)
                Command.STATUS -> requestStatus()
                else -> {}
            }
        }
    }

    SwipeToDismissBox(
        onDismissed = {
            if (currentScreen != Screen.HOME) {
                currentScreen = Screen.HOME
            }
        },
        userSwipeEnabled = currentScreen != Screen.HOME,
    ) { isDismissed ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    state = state,
                    busy = busy,
                    note = note,
                    onConnectToggle = {
                        scope.launch { runCommand(Command.forCurrentState(StateStore.current().state)) }
                    },
                    onRefresh = { scope.launch { requestStatus() } },
                    onNavigateToPeers = { currentScreen = Screen.PEERS },
                    onNavigateToResources = { currentScreen = Screen.RESOURCES },
                )
                Screen.PEERS -> PeersScreen(
                    peers = state.peers,
                    onBack = { currentScreen = Screen.HOME },
                )
                Screen.RESOURCES -> ResourcesScreen(
                    resources = state.resources,
                    busy = busy,
                    onToggleRoute = { name, isSelected ->
                        scope.launch {
                            runCommand(
                                if (isSelected) Command.DESELECT_ROUTE else Command.SELECT_ROUTE,
                                route = name,
                            )
                        }
                    },
                    onBack = { currentScreen = Screen.HOME },
                )
            }

            TimeText(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: BridgeState,
    busy: Boolean,
    note: String?,
    onConnectToggle: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToPeers: () -> Unit,
    onNavigateToResources: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .requestFocusOnHierarchyActive()
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.behavior(scrollState),
                focusRequester = focusRequester,
            )
            .verticalScroll(scrollState)
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusGlyph(state = state.state, busy = busy)
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(statusLabelRes(state.state)),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        // Node IP / FQDN display
        val nodeIp = state.ip?.lineSequence()?.firstOrNull()?.trim()
        val nodeFqdn = state.fqdn?.trim()
        if (!nodeIp.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = nodeIp,
                color = NetBirdOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        } else if (!nodeFqdn.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = nodeFqdn,
                color = NetBirdOrange,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val deviceName = state.deviceName
        if (!deviceName.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.label_device, deviceName),
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val detail = note ?: detailFor(state) ?: hintFor(state.state)
        if (detail != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = detail,
                color = Muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onConnectToggle,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(0.92f),
            colors = ButtonDefaults.buttonColors(
                containerColor = NetBirdOrange,
                contentColor = Color(0xFF181A1D),
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_netbird),
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = stringResource(
                    if (state.state.isConnected || state.state == ConnectionState.CONNECTING) {
                        R.string.action_disconnect
                    } else {
                        R.string.action_connect
                    },
                ),
                fontWeight = FontWeight.Medium,
            )
        }

        // Navigation to Peers & Resources
        if (state.state.isConnected) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.92f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CompactButton(
                    onClick = onNavigateToPeers,
                    modifier = Modifier.weight(1f).padding(end = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface),
                ) {
                    Text(
                        text = "${stringResource(R.string.label_peers)} (${state.peers.size})",
                        fontSize = 11.sp,
                        color = Color.White,
                    )
                }
                CompactButton(
                    onClick = onNavigateToResources,
                    modifier = Modifier.weight(1f).padding(start = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface),
                ) {
                    Text(
                        text = "${stringResource(R.string.label_resources)} (${state.resources.size})",
                        fontSize = 11.sp,
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        CompactButton(
            onClick = onRefresh,
            enabled = !busy,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(13.dp).padding(end = 4.dp),
            )
            Text(text = stringResource(R.string.action_refresh), fontSize = 10.sp)
        }
    }
}

@Composable
private fun PeersScreen(
    peers: List<PeerItem>,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .requestFocusOnHierarchyActive()
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.behavior(scrollState),
                focusRequester = focusRequester,
            )
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Centered, well-spaced header with prominent back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = NetBirdOrange,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.label_peers),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        if (peers.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.label_no_peers),
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            // Sort peers alphabetically by FQDN/IP
            val sortedPeers = remember(peers) {
                peers.sortedBy { (it.fqdn.ifBlank { it.ip }).lowercase() }
            }
            sortedPeers.forEach { peer ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = peer.fqdn.ifBlank { peer.ip },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = peer.ip,
                                color = Muted,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (peer.isConnected) Green else Grey),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourcesScreen(
    resources: List<ResourceItem>,
    busy: Boolean,
    onToggleRoute: (name: String, isSelected: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .requestFocusOnHierarchyActive()
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.behavior(scrollState),
                focusRequester = focusRequester,
            )
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Centered, well-spaced header with prominent back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = NetBirdOrange,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.label_resources),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        if (resources.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.label_no_resources),
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            // Sort resources alphabetically by name (or network if name is empty)
            val sortedResources = remember(resources) {
                resources.sortedBy { (it.name.ifBlank { it.network }).lowercase() }
            }
            sortedResources.forEach { res ->
                SwitchButton(
                    checked = res.isSelected,
                    onCheckedChange = { onToggleRoute(res.name, res.isSelected) },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    label = {
                        Text(
                            text = res.name.ifBlank { res.network },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = res.network,
                            fontSize = 10.sp,
                            color = Muted,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusGlyph(state: ConnectionState, busy: Boolean) {
    val color = statusColor(state)
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_netbird),
            contentDescription = null,
            modifier = Modifier.size(width = 34.dp, height = 26.dp),
        )
        if (busy || state.isBusy) {
            Spinner(color = NetBirdOrange)
        }
        if (state == ConnectionState.ERROR || state == ConnectionState.NEEDS_LOGIN ||
            state == ConnectionState.NEEDS_SETUP || state == ConnectionState.NO_NETWORK ||
            state == ConnectionState.PHONE_UNAVAILABLE
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.align(Alignment.BottomEnd).size(16.dp),
            )
        }
    }
}

@Composable
private fun Spinner(color: Color) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier = Modifier.size(48.dp)) {
        rotate(angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 280f,
                useCenter = false,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

private fun detailFor(state: BridgeState): String? = state.message

@Composable
private fun hintFor(state: ConnectionState): String? = when (state) {
    ConnectionState.PHONE_UNAVAILABLE -> stringResource(R.string.hint_phone_unavailable)
    ConnectionState.NEEDS_LOGIN -> stringResource(R.string.hint_needs_login)
    ConnectionState.NEEDS_SETUP -> stringResource(R.string.hint_needs_setup)
    else -> null
}

private fun statusColor(state: ConnectionState): Color = when (state) {
    ConnectionState.CONNECTED -> NetBirdOrange
    ConnectionState.CONNECTING, ConnectionState.DISCONNECTING, ConnectionState.UNKNOWN -> Amber
    ConnectionState.NEEDS_LOGIN, ConnectionState.NEEDS_SETUP, ConnectionState.ERROR -> Red
    ConnectionState.NO_NETWORK -> Grey
    ConnectionState.PHONE_UNAVAILABLE -> Grey
    ConnectionState.DISCONNECTED -> Muted
}

@StringRes
private fun statusLabelRes(state: ConnectionState): Int = when (state) {
    ConnectionState.CONNECTED -> R.string.status_connected
    ConnectionState.CONNECTING -> R.string.status_connecting
    ConnectionState.DISCONNECTING -> R.string.status_disconnecting
    ConnectionState.DISCONNECTED -> R.string.status_disconnected
    ConnectionState.NEEDS_LOGIN -> R.string.status_needs_login
    ConnectionState.NEEDS_SETUP -> R.string.status_needs_setup
    ConnectionState.NO_NETWORK -> R.string.status_no_network
    ConnectionState.ERROR -> R.string.status_error
    ConnectionState.PHONE_UNAVAILABLE -> R.string.status_phone_unavailable
    ConnectionState.UNKNOWN -> R.string.status_unknown
}
