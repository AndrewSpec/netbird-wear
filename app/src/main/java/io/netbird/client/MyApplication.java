package io.netbird.client;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.wearable.Wearable;

import io.netbird.client.wear.BridgeController;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Set Theme at start
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(themeMode);

        // Advertise the Wear bridge capability so the watch can discover this
        // phone. Guarded because the app also runs on devices without Play
        // services (e.g. Android TV / emulators).
        try {
            Wearable.getCapabilityClient(this)
                    .addLocalCapability(BridgeController.CAPABILITY)
                    .addOnSuccessListener(ignored ->
                            Log.i("NetBirdWearBridge", "capability registered: "
                                    + BridgeController.CAPABILITY))
                    .addOnFailureListener(e ->
                            Log.w("NetBirdWearBridge", "capability registration failed", e));
        } catch (Throwable t) {
            Log.w("NetBirdWearBridge", "Wearable Data Layer unavailable", t);
        }
    }
}