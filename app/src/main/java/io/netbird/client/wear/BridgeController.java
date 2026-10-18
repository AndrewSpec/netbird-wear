package io.netbird.client.wear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.netbird.client.MainActivity;
import io.netbird.client.R;
import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.VPNService;
import io.netbird.client.ui.home.Resource;
import io.netbird.gomobile.android.Android;
import io.netbird.gomobile.android.ConnectionListener;
import io.netbird.gomobile.android.NetworkArray;
import io.netbird.gomobile.android.PeerInfo;
import io.netbird.gomobile.android.PeerInfoArray;

/**
 * Phone-side bridge between the Wear OS Data Layer and the NetBird VPN engine.
 */
public final class BridgeController implements ServiceConnection, ServiceStateListener, ConnectionListener {
    private static final String TAG = "NetBirdWearBridge";

    public static final String CAPABILITY = "netbird_wear_bridge";
    public static final String PATH_COMMAND = "/netbird/command";
    public static final String PATH_STATE = "/netbird/state";

    public static final String ACTION_STATE_CHANGED = "io.netbird.client.automation.STATE_CHANGED";

    public static final String CMD_CONNECT = "connect";
    public static final String CMD_DISCONNECT = "disconnect";
    public static final String CMD_TOGGLE = "toggle";
    public static final String CMD_STATUS = "status";
    public static final String CMD_SELECT_ROUTE = "select_route";
    public static final String CMD_DESELECT_ROUTE = "deselect_route";

    private static final String SETUP_CHANNEL_ID = "netbird_wear_setup";
    private static final int SETUP_NOTIFICATION_ID = 2101;
    private static final int PERMISSION_NOTIFICATION_ID = 2102;

    private static volatile BridgeController instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> pendingActions = new ArrayList<>();

    private VPNService.MyLocalBinder binder;
    private boolean binding;
    private String lastMessage;
    private String localFqdn = "";
    private String localIp = "";

    private BridgeController(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static BridgeController get(Context context) {
        BridgeController local = instance;
        if (local == null) {
            synchronized (BridgeController.class) {
                local = instance;
                if (local == null) {
                    local = new BridgeController(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Entry point for watch messages and automation broadcasts. */
    public void handleCommand(final String command, final String requestId) {
        handleCommand(command, requestId, null);
    }

    public void handleCommand(final String command, final String requestId, final String route) {
        if (command == null) {
            return;
        }
        Log.i(TAG, "handleCommand " + command + (route != null ? (" route=" + route) : ""));
        ensureBound(new Runnable() {
            @Override
            public void run() {
                execute(command, requestId, route);
            }
        });
    }

    private void execute(String command, String requestId, String route) {
        VPNService.MyLocalBinder localBinder = binder;
        if (localBinder == null) {
            sendState(WearState.UNKNOWN, "NetBird VPN service is not available", requestId);
            return;
        }
        switch (command) {
            case CMD_CONNECT:
                connect(requestId);
                break;
            case CMD_DISCONNECT:
                disconnect(requestId);
                break;
            case CMD_TOGGLE:
                if (localBinder.isRunning()) {
                    disconnect(requestId);
                } else {
                    connect(requestId);
                }
                break;
            case CMD_SELECT_ROUTE:
                if (route != null && !route.isEmpty()) {
                    try {
                        localBinder.selectRoute(route);
                    } catch (Exception e) {
                        Log.w(TAG, "error selecting route " + route, e);
                    }
                }
                sendState(currentState(), lastMessage, requestId);
                break;
            case CMD_DESELECT_ROUTE:
                if (route != null && !route.isEmpty()) {
                    try {
                        localBinder.deselectRoute(route);
                    } catch (Exception e) {
                        Log.w(TAG, "error deselecting route " + route, e);
                    }
                }
                sendState(currentState(), lastMessage, requestId);
                break;
            case CMD_STATUS:
            default:
                sendState(currentState(), lastMessage, requestId);
                break;
        }
    }

    private void connect(final String requestId) {
        final VPNService.MyLocalBinder localBinder = binder;
        if (localBinder == null) {
            return;
        }
        if (localBinder.isRunning()) {
            sendState(currentState(), lastMessage, requestId);
            return;
        }
        if (VpnService.prepare(appContext) != null) {
            notifySetup("Open NetBird on the phone and allow the VPN request");
            sendState(WearState.NEEDS_SETUP,
                    "Open NetBird on the phone and allow the VPN request", requestId);
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent start = new Intent(appContext, VPNService.class);
                    start.setAction(VPNService.INTENT_ACTION_START);
                    appContext.startForegroundService(start);
                    Log.i(TAG, "started foreground VPN service");
                } catch (Exception e) {
                    if (isForegroundStartRestriction(e)) {
                        notifyBatteryExemption();
                        sendState(WearState.NEEDS_SETUP,
                                "Allow NetBird to run in the background (turn off battery optimisation)",
                                requestId);
                        return;
                    }
                    Log.w(TAG, "could not start the VPN service", e);
                    sendState(WearState.ERROR, e.getMessage(), requestId);
                    return;
                }
                localBinder.runEngine(null, false);
                sendState(WearState.CONNECTING, null, requestId);
            }
        });
    }

    private void disconnect(final String requestId) {
        final VPNService.MyLocalBinder localBinder = binder;
        if (localBinder == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                localBinder.stopEngine();
                sendState(WearState.DISCONNECTING, null, requestId);
            }
        });
    }

    private void ensureBound(final Runnable action) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (binder != null) {
                    action.run();
                    return;
                }
                synchronized (BridgeController.this) {
                    pendingActions.add(action);
                    if (binding) {
                        return;
                    }
                    binding = true;
                }
                Intent intent = new Intent(appContext, VPNService.class);
                boolean started;
                try {
                    started = appContext.bindService(intent, BridgeController.this, Context.BIND_AUTO_CREATE);
                } catch (Throwable t) {
                    Log.w(TAG, "bindService failed", t);
                    started = false;
                }
                if (!started) {
                    Log.w(TAG, "bindService to VPNService failed");
                    synchronized (BridgeController.this) {
                        binding = false;
                        pendingActions.clear();
                    }
                    sendState(WearState.UNKNOWN, "Could not bind the NetBird VPN service", null);
                } else {
                    Log.i(TAG, "binding to VPNService");
                }
            }
        });
    }

    // ---------------------------------------------------------------------
    // ServiceConnection
    // ---------------------------------------------------------------------

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (VPNService.MyLocalBinder) service;
        binding = false;
        Log.i(TAG, "bound to VPNService");
        try {
            binder.addServiceStateListener(this);
            binder.addConnectionObserver(this);
        } catch (Throwable t) {
            Log.w(TAG, "could not register engine listeners", t);
        }
        List<Runnable> actions;
        synchronized (this) {
            actions = new ArrayList<>(pendingActions);
            pendingActions.clear();
        }
        for (Runnable action : actions) {
            action.run();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
        binding = false;
    }

    @Override
    public void onBindingDied(ComponentName name) {
        onServiceDisconnected(name);
    }

    @Override
    public void onNullBinding(ComponentName name) {
        onServiceDisconnected(name);
    }

    // ---------------------------------------------------------------------
    // Engine callbacks
    // ---------------------------------------------------------------------

    @Override
    public void onStarted() {
        publish(WearState.CONNECTING, null);
    }

    @Override
    public void onStopped() {
        localFqdn = "";
        localIp = "";
        publish(WearState.DISCONNECTED, null);
    }

    @Override
    public void onError(String msg) {
        publish(WearState.ERROR, msg);
    }

    @Override
    public void onAddressChanged(String fqdn, String ip) {
        localFqdn = fqdn != null ? fqdn : "";
        localIp = ip != null ? ip : "";
        publish(currentState(), null);
    }

    @Override
    public void onConnected() {
        publish(WearState.CONNECTED, null);
    }

    @Override
    public void onConnecting() {
        publish(WearState.CONNECTING, null);
    }

    @Override
    public void onDisconnected() {
        publish(WearState.DISCONNECTED, null);
    }

    @Override
    public void onDisconnecting() {
        publish(WearState.DISCONNECTING, null);
    }

    @Override
    public void onPeersListChanged(long numberOfPeers) {
        publish(currentState(), null);
    }

    @Override
    public void onStateChanged(long state) {
        if (state == Android.ClientStateNoNetwork) {
            publish(WearState.NO_NETWORK, null);
        }
    }

    // ---------------------------------------------------------------------
    // State mapping / delivery
    // ---------------------------------------------------------------------

    private WearState currentState() {
        VPNService.MyLocalBinder localBinder = binder;
        if (localBinder == null) {
            return WearState.UNKNOWN;
        }
        try {
            if (localBinder.isLoginRequired()) {
                return WearState.NEEDS_LOGIN;
            }
            if (!localBinder.isRunning()) {
                return WearState.DISCONNECTED;
            }
            String status = localBinder.status();
            if ("Connected".equals(status)) {
                return WearState.CONNECTED;
            }
            if ("Connecting".equals(status)) {
                return WearState.CONNECTING;
            }
            if ("NeedsLogin".equals(status)
                    || "LoginFailed".equals(status)
                    || "SessionExpired".equals(status)) {
                return WearState.NEEDS_LOGIN;
            }
            return WearState.CONNECTING;
        } catch (Throwable t) {
            Log.w(TAG, "could not read engine state", t);
            return WearState.ERROR;
        }
    }

    private void publish(final WearState state, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                sendState(state, message, null);
            }
        });
    }

    void sendState(WearState state, String message, String requestId) {
        if (state == null) {
            state = WearState.UNKNOWN;
        }
        if (requestId == null || requestId.isEmpty()) {
            lastMessage = message;
        }
        final WearState outgoingState = state;
        try {
            JSONObject json = new JSONObject();
            json.put("state", outgoingState.wire);
            if (message != null && !message.isEmpty()) {
                json.put("message", message);
            }
            String deviceName = Build.MODEL;
            if (deviceName == null || deviceName.isEmpty()) {
                deviceName = "Android";
            }
            json.put("deviceName", deviceName);
            if (requestId != null && !requestId.isEmpty()) {
                json.put("requestId", requestId);
            }
            json.put("timestamp", System.currentTimeMillis());

            // Node networking info
            json.put("fqdn", localFqdn);
            json.put("ip", localIp);

            // Populate peers and resources if connected or binder available
            VPNService.MyLocalBinder localBinder = binder;
            JSONArray peersArray = new JSONArray();
            JSONArray resourcesArray = new JSONArray();

            if (localBinder != null && localBinder.isRunning()) {
                try {
                    PeerInfoArray pInfo = localBinder.peersInfo();
                    if (pInfo != null) {
                        for (int i = 0; i < pInfo.size(); i++) {
                            PeerInfo pi = pInfo.get(i);
                            JSONObject pObj = new JSONObject();
                            pObj.put("fqdn", pi.getFQDN());
                            pObj.put("ip", pi.getIP());
                            pObj.put("ipv6", pi.getIPv6());
                            pObj.put("status", pi.getConnStatus());
                            pObj.put("latency", pi.getLatencyMs());
                            peersArray.put(pObj);
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "could not serialize peers", t);
                }

                try {
                    NetworkArray nInfo = localBinder.networks();
                    if (nInfo != null) {
                        for (int i = 0; i < nInfo.size(); i++) {
                            var net = nInfo.get(i);
                            if (Resource.isExitNodeAddress(net.getNetwork())) {
                                continue;
                            }
                            JSONObject rObj = new JSONObject();
                            rObj.put("name", net.getName());
                            rObj.put("network", net.getNetwork());
                            rObj.put("status", net.getStatus());
                            rObj.put("selected", net.getIsSelected());
                            resourcesArray.put(rObj);
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "could not serialize resources", t);
                }
            }

            json.put("peers", peersArray);
            json.put("resources", resourcesArray);

            byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);

            MessageClient messageClient = Wearable.getMessageClient(appContext);
            Wearable.getNodeClient(appContext).getConnectedNodes()
                    .addOnSuccessListener(nodes -> {
                        Log.i(TAG, "publishing " + outgoingState.wire + " to " + nodes.size() + " node(s)");
                        for (Node node : nodes) {
                            messageClient.sendMessage(node.getId(), PATH_STATE, payload);
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "no watch nodes connected", e));

            Intent broadcast = new Intent(ACTION_STATE_CHANGED)
                    .putExtra("state", outgoingState.wire)
                    .putExtra("message", message == null ? "" : message);
            appContext.sendBroadcast(broadcast, "io.netbird.client.permission.CONTROL_VPN");
        } catch (Throwable t) {
            Log.w(TAG, "could not publish state", t);
        }
    }

    private static boolean isForegroundStartRestriction(Throwable t) {
        if (t == null) {
            return false;
        }
        String name = t.getClass().getName();
        if (name.contains("ForegroundServiceStartNotAllowedException")) {
            return true;
        }
        String message = t.getMessage();
        return message != null && message.contains("startForegroundService() not allowed");
    }

    private boolean isBatteryOptimisationExempt() {
        try {
            PowerManager powerManager =
                    (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            return powerManager != null
                    && powerManager.isIgnoringBatteryOptimizations(appContext.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void notifySetup(String text) {
        try {
            NotificationManager manager = notificationManager();
            if (manager == null) {
                return;
            }
            Intent open = new Intent(appContext, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    appContext, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new NotificationCompat.Builder(appContext, SETUP_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_netbird_tile)
                    .setContentTitle("NetBird Wear")
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build();
            manager.notify(SETUP_NOTIFICATION_ID, notification);
        } catch (Throwable t) {
            Log.w(TAG, "could not post setup notification", t);
        }
    }

    private void notifyBatteryExemption() {
        try {
            NotificationManager manager = notificationManager();
            if (manager == null) {
                return;
            }
            Intent request = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + appContext.getPackageName()));
            request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    appContext, 1, request,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new NotificationCompat.Builder(appContext, SETUP_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_netbird_tile)
                    .setContentTitle("NetBird Wear — allow background control")
                    .setContentText("Turn off battery optimisation so the watch can connect NetBird")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build();
            manager.notify(PERMISSION_NOTIFICATION_ID, notification);
        } catch (Throwable t) {
            Log.w(TAG, "could not post battery optimisation notification", t);
        }
    }

    private NotificationManager notificationManager() {
        NotificationManager manager =
                (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    SETUP_CHANNEL_ID,
                    "NetBird Wear",
                    NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        return manager;
    }
}
