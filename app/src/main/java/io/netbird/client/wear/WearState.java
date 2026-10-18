package io.netbird.client.wear;

/** Connection states shared with the Wear OS app. Keep wire names in sync. */
public enum WearState {
    UNKNOWN("UNKNOWN"),
    DISCONNECTED("DISCONNECTED"),
    CONNECTING("CONNECTING"),
    CONNECTED("CONNECTED"),
    DISCONNECTING("DISCONNECTING"),
    NEEDS_LOGIN("NEEDS_LOGIN"),
    NEEDS_SETUP("NEEDS_SETUP"),
    NO_NETWORK("NO_NETWORK"),
    ERROR("ERROR");

    public final String wire;

    WearState(String wire) {
        this.wire = wire;
    }
}
