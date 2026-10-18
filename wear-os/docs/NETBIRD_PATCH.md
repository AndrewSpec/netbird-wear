# NetBird Wear Edition: Android-client patch

This patch adds a Wear OS control bridge to the GPL-licensed NetBird Android
client. The resulting phone app is an **unofficial community build**, not an
official NetBird release. The bridge uses the same engine-control operations as
the built-in Quick Settings Tile:
`VPNService.MyLocalBinder.runEngine()` and `stopEngine()`.

The supported base is Android upstream commit
`ad7865a2c3cc1bc940bf63512ccab1819ebf53a6`. This document describes the current
standalone watch project plus patch. In the planned publication fork, the Android
client will be at the repository root, with the watch project, documentation, and
scripts under `wear-os/`, in a single customization commit above that pinned base.
That relocation is not assumed in the commands below.

## Files and integration points

The patch adds these files inside the upstream Android clone:

```text
app/src/main/java/io/netbird/client/wear/
├── WearState.java            # States and wire names sent to the watch
├── BridgeController.java     # Binding, commands, status, and setup notifications
├── WearBridgeService.java    # WearableListenerService for /netbird/command
└── AutomationReceiver.java   # Signature-protected broadcast control API
```

Changes to existing upstream files:

| Upstream location | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | Declares `WearBridgeService`, `AutomationReceiver`, the signature-level `CONTROL_VPN` permission, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. |
| `app/build.gradle.kts` | Adds `com.google.android.gms:play-services-wearable:18.2.0`. |
| `MyApplication.java` | Registers the `netbird_wear_bridge` capability with `CapabilityClient`. |
| `tool/.../VPNService.java` | Exposes `MyLocalBinder.status()` and connection-observer registration without replacing the UI listener. |
| `tool/build.gradle.kts` | Enables default Android API return values in local JVM tests. |
| `tool/src/test/.../ConnectionObserversUnitTest.java` | Adds observer fan-out regressions covering simultaneous UI/bridge events, UI detachment/reattachment, and duplicate registration. |

The patch itself is [netbird-wear.patch](../patches/netbird-wear.patch).

## Automation security (1.0.3 onward)

The command receiver requires
`io.netbird.client.permission.CONTROL_VPN` with `protectionLevel="signature"`.
Recipients of status broadcasts must also hold this permission. Ordinary
third-party apps, including Tasker, and unprivileged ADB commands cannot toggle
the VPN through this broadcast API. Trusted integrations need a matching signing
identity and the required permission; this is not a general-purpose public API.

Watch control continues to use the Wearable Data Layer rather than opening this
broadcast API to other apps. **Both APKs must have the same package
(`io.netbird.client`) and signing certificate.** Pairing alone does not authorize
an arbitrary watch app to send this project's Data Layer commands. See
[the security policy](../SECURITY.md).

## Wearable Data Layer protocol

- Phone capability: `netbird_wear_bridge`.
- Watch → phone: `/netbird/command`, with a JSON payload:
  ```json
  {"command":"connect|disconnect|toggle|status","requestId":"<uuid>"}
  ```
- Phone → watch: `/netbird/state`, for example:
  ```json
  {"state":"CONNECTED","message":null,"deviceName":"Phone","requestId":"<uuid>","timestamp":1700000000000}
  ```
- The phone sends state after engine changes and in response to status requests.
  A direct response echoes `requestId` so the watch can correlate it with a command.
  `timestamp` is epoch time in milliseconds.

Google Play services routes messages within the matching application identity
(package + certificate, or AppKey). A mismatch can produce the log message
`Failed to deliver message ... Didn't find package ...`. The old watch package
`io.netbird.wear` does not satisfy the phone's `io.netbird.client` identity.

## Phone-screen synchronization

The bridge registers through `binder.addConnectionObserver(this)`, using
`EngineRunner`'s existing observer fan-out. It must not use
`setConnectionStateListener(this)`: that method is reserved for `MainActivity`
and replaces its listener. Using it previously prevented the open phone screen
from refreshing after watch commands.

The independent bridge observer can remain active when the UI detaches, and
reattaching the UI must not disable watch updates. Unit regressions and historical
device results cover this behavior; they do not guarantee that the latest public
APK has been tested on hardware. See [the test record](TESTING.md).

## State mapping

| Watch state | NetBird source |
|---|---|
| `CONNECTED` | `status() == "Connected"` or `onConnected` |
| `CONNECTING` | `status() == "Connecting"`, `onStarted`, or `onConnecting` |
| `DISCONNECTING` | `onDisconnecting` or a stop command |
| `DISCONNECTED` | `!isRunning()`, `onStopped`, or `onDisconnected` |
| `NEEDS_LOGIN` | `isLoginRequired()` or status `NeedsLogin`, `LoginFailed`, or `SessionExpired` |
| `NEEDS_SETUP` | Missing VPN consent (`VpnService.prepare() != null`) or denied background foreground-service start |
| `NO_NETWORK` | `ConnectionListener.onStateChanged(ClientStateNoNetwork)` |
| `ERROR` | Service error or another failed control operation |

The watch uses `PHONE_UNAVAILABLE` locally when discovery or a response fails.
Cached status is not a guarantee of the phone's current VPN state.

## Background connection starts on Android 12+

A watch command reaches `WearableListenerService` while the phone app may be in
the background. Starting the VPN requires a foreground service. Android 12+
restricts background foreground-service starts unless an applicable exemption
exists; a user-approved battery-optimization exemption is the route used here.

1. `BridgeController.connect()` first checks VPN consent. If it is missing, the
   bridge returns `NEEDS_SETUP` and offers a notification to open the phone app.
2. It calls `startForegroundService(...)`, following the Quick Settings Tile's
   service-start path, then drives the engine through the binder.
3. If a foreground-start restriction is detected, it returns `NEEDS_SETUP` and
   shows a notification opening the system battery-optimization exemption dialog.
4. After the exemption, background connection can be attempted again, including
   with the phone screen off. Manufacturer restrictions still need device testing.

Disconnecting an already running VPN does not need to start a new foreground
service. It still depends on a reachable, functioning phone; it is not guaranteed
to work during every failure condition.

## Applying the patch

Run from the current standalone project's root:

```bash
./scripts/apply-netbird-patch.sh /path/to/android-client
```

Use a clone at pinned commit
`ad7865a2c3cc1bc940bf63512ccab1819ebf53a6`. The
[patch helper](../scripts/apply-netbird-patch.sh) checks whether the patch applies
cleanly or is already applied. It refuses incompatible trees without resetting
or deleting their changes. An older modified clone may need to be replaced by a
separate clean build clone; do not discard unrelated work to force application.

## Building the Go AAR

The phone build needs `gomobile/netbird.aar` generated from the pinned Android
client's `netbird` Go submodule. Run
[build-netbird-aar.sh](../scripts/build-netbird-aar.sh) from this project:

```bash
./scripts/build-netbird-aar.sh
```

The script uses:

- NDK 23.1.7779620 (r23b), matching the upstream Android build pin.
- `gomobile` and `gobind` versions selected from the pinned engine's module dependencies.
- `gomobile bind -target=android/arm64 -androidapi 26 ... client/android`.

The default cache is `.build/netbird.aar`; the patch helper copies it into the
Android clone when available. Public phone APKs are ARM64-only. An x86_64 emulator
requires a separately built AAR with `android/amd64` added to the target list;
the shipped scripts and APKs do not provide that ABI by default.

## Signing and validation

The phone and watch builds must share a certificate. The current helper scripts
use a workspace-local debug/developer keystore for sideloading, not an official
NetBird production key. Preserve it for updates. An official client signed by a
different key must be uninstalled first, deleting its local data.

The 1.0.3 public build's manifest protection was checked automatically, but the
APK pair has **not** been physically tested. Follow [the manual checklist](TESTING.md)
for watch control, phone-UI synchronization, locked-phone operation, Tile layout,
and permission-denial regression tests.
