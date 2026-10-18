package io.netbird.client.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Restricted automation surface for same-signature companion applications.
 * The manifest requires io.netbird.client.permission.CONTROL_VPN. Ordinary
 * third-party apps cannot toggle the VPN through this receiver.
 *
 * Actions:
 * - io.netbird.client.automation.CONNECT
 * - io.netbird.client.automation.DISCONNECT
 * - io.netbird.client.automation.TOGGLE
 * - io.netbird.client.automation.STATUS
 *
 * State changes are announced with an implicit io.netbird.client.automation.STATE_CHANGED
 * broadcast carrying the "state" and "message" extras.
 */
public class AutomationReceiver extends BroadcastReceiver {

    public static final String ACTION_CONNECT = "io.netbird.client.automation.CONNECT";
    public static final String ACTION_DISCONNECT = "io.netbird.client.automation.DISCONNECT";
    public static final String ACTION_TOGGLE = "io.netbird.client.automation.TOGGLE";
    public static final String ACTION_STATUS = "io.netbird.client.automation.STATUS";

    private static final String TAG = "NetBirdAutomation";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        String command;
        switch (action) {
            case ACTION_CONNECT:
                command = BridgeController.CMD_CONNECT;
                break;
            case ACTION_DISCONNECT:
                command = BridgeController.CMD_DISCONNECT;
                break;
            case ACTION_TOGGLE:
                command = BridgeController.CMD_TOGGLE;
                break;
            case ACTION_STATUS:
                command = BridgeController.CMD_STATUS;
                break;
            default:
                Log.w(TAG, "unknown action: " + action);
                return;
        }
        BridgeController.get(context).handleCommand(command, null);
    }
}
