package io.netbird.client.tool;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import io.netbird.gomobile.android.ConnectionListener;

/** Regression coverage for using the service observer API for the Wear bridge. */
public class ConnectionObserversUnitTest {
    private static class RecordingListener implements ConnectionListener {
        final List<String> events = new ArrayList<>();
        public void onConnecting() { events.add("connecting"); }
        public void onConnected() { events.add("connected"); }
        public void onDisconnecting() { events.add("disconnecting"); }
        public void onDisconnected() { events.add("disconnected"); }
        public void onStateChanged(long s) { events.add("state:" + s); }
        public void onAddressChanged(String f, String i) { events.add(f + ":" + i); }
        public void onPeersListChanged(long n) { events.add("peers:" + n); }
    }

    // Exercise the real upstream fan-out without constructing a Go VPN engine.
    private ConnectionListener dispatcher(ConnectionListener ui, Set<ConnectionListener> observers)
            throws Exception {
        Class<?> cls = Class.forName(EngineRunner.class.getName() + "$ObservingConnectionListener");
        Constructor<?> ctor = cls.getDeclaredConstructor(ConnectionListener.class, Set.class, Set.class);
        ctor.setAccessible(true);
        return (ConnectionListener) ctor.newInstance(ui, new HashSet<Runnable>(), observers);
    }

    private void connectionCycle(ConnectionListener listener) {
        listener.onConnecting();
        listener.onConnected();
        listener.onAddressChanged("phone.netbird", "100.64.0.1");
        listener.onPeersListChanged(3);
        listener.onStateChanged(5);
        listener.onDisconnecting();
        listener.onDisconnected();
    }

    @Test public void uiAndWatchReceiveEveryEvent() throws Exception {
        RecordingListener ui = new RecordingListener();
        RecordingListener watch = new RecordingListener();
        Set<ConnectionListener> observers = new HashSet<>();
        observers.add(watch);
        connectionCycle(dispatcher(ui, observers));
        assertEquals(7, ui.events.size());
        assertEquals(ui.events, watch.events);
    }

    @Test public void watchKeepsReceivingAfterUiUnbindsAndRebinds() throws Exception {
        RecordingListener watch = new RecordingListener();
        Set<ConnectionListener> observers = new HashSet<>();
        observers.add(watch);
        Field field = EngineRunner.class.getDeclaredField("NO_OP_CONNECTION_LISTENER");
        field.setAccessible(true);
        connectionCycle(dispatcher((ConnectionListener) field.get(null), observers));
        assertEquals(7, watch.events.size());
        RecordingListener newUi = new RecordingListener();
        connectionCycle(dispatcher(newUi, observers));
        assertEquals(14, watch.events.size());
        assertEquals(newUi.events, watch.events.subList(7, 14));
    }

    @Test public void duplicateWatchRegistrationDoesNotDuplicateCallbacks() throws Exception {
        RecordingListener ui = new RecordingListener();
        RecordingListener watch = new RecordingListener();
        Set<ConnectionListener> observers = new HashSet<>();
        observers.add(watch);
        observers.add(watch);
        connectionCycle(dispatcher(ui, observers));
        assertEquals(7, watch.events.size());
    }
}
