package io.netbird.client.wear;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Receives commands from the paired Wear OS app and hands them to
 * {@link BridgeController}.
 *
 * Declared in the manifest with the MESSAGE_RECEIVED intent filter; Google Play
 * services starts it (and this app's process) when a message arrives, so the
 * bridge works even if the NetBird UI is not running.
 */
public class WearBridgeService extends WearableListenerService {

    private static final String TAG = "NetBirdWearService";

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        if (!BridgeController.PATH_COMMAND.equals(messageEvent.getPath())) {
            return;
        }
        try {
            JSONObject json = new JSONObject(
                    new String(messageEvent.getData(), StandardCharsets.UTF_8));
            String command = json.optString("command", "");
            String requestId = json.optString("requestId", "");
            String route = json.optString("route", null);
            Log.i(TAG, "command from node " + messageEvent.getSourceNodeId()
                    + ": " + command + " requestId=" + requestId + (route != null ? (" route=" + route) : ""));
            BridgeController.get(this).handleCommand(command, requestId, route);
        } catch (Exception e) {
            Log.w(TAG, "could not parse watch command", e);
        }
    }
}
