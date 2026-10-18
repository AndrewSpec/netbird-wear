# Plan: control the phone's NetBird VPN from Wear OS

## 1. Goal and scope

Provide a Wear OS app for a paired Android phone that can:

- Display NetBird connection status, including transitional, login, setup, and error states.
- Connect and disconnect the **phone's** NetBird VPN from the watch.
- Provide a Tile for quick access to the same controls.

The target is Wear OS 3+ (`minSdk 30`), including the Galaxy Watch 4. This is not a
standalone VPN client for the watch. It is an **unofficial community project**, not
an official NetBird product or a project affiliated with NetBird GmbH.

## 2. Why the phone client needs a patch

The Android client at pinned upstream commit
`ad7865a2c3cc1bc940bf63512ccab1819ebf53a6` does not expose a supported external
connect/disconnect API:

- `io.netbird.client.tool.VPNService` has `android:exported="false"`, so an
  unrelated app cannot start it or bind to its local `Binder`.
- Its foreground-service notification does not provide connect/disconnect actions.
- `MainActivity` is exported but does not handle connect/disconnect commands.

Upstream feature requests, such as
[android-client#81](https://github.com/netbirdio/android-client/issues/81) and
[android-client#264](https://github.com/netbirdio/android-client/issues/264), are
background context, not evidence that such an API is available or promised. Their
current status is not assumed here. This analysis applies to the pinned source,
not to every future upstream release.

A separate watch app cannot provide the intended reliable, ordinary screen-off
control of the unmodified client. Accessibility-based UI automation depends on
phone UI state, and privileged approaches such as root or Shizuku add requirements
outside this project's scope. The selected approach is therefore an unofficial
build of the upstream phone client with a Wear bridge, plus a separate watch app.

## 3. Approaches considered

| Approach | How it works | Constraints | Decision |
|---|---|---|---|
| **A. NetBird Wear Edition + watch app** | Add a Data Layer bridge to the Android client and control `VPNService` through its local `Binder`. | Replaces the official phone app; requires matching phone/watch package and certificate, initial setup, and a background-start exemption. | **Selected** |
| B. Separate companion + AccessibilityService | Automate the official client's UI. | Depends on accessibility permission, screen/UI state, and UI stability. | Not implemented or committed to the roadmap. |
| C. Standalone watch VPN client | Run a VPN on the watch itself. | A different product with separate authentication and platform requirements; does not control the phone's VPN. | Out of scope. |

Approach A retains the upstream client's existing features while adding remote
connection control. Compatibility with newer upstream revisions must be reviewed;
a small patch is not a guarantee that future rebases will apply unchanged.

## 4. Current layout and planned publication

**Current checkout:** a standalone Gradle watch project with the [watch module](../wear/),
[build scripts](../scripts/), and [Android-client patch](../patches/netbird-wear.patch).
The phone client is built in a separate upstream clone managed by the scripts.
All paths and commands in this documentation describe that current layout.

**Planned publication:** a fork of the Android client, with the Android upstream
project at the repository root and the standalone watch project, documentation,
and scripts under `wear-os/`. The intended publication history is **one
customization commit above Android upstream commit
`ad7865a2c3cc1bc940bf63512ccab1819ebf53a6`**. The relocation and root publishing
README are separate work; the present checkout must not be described as already
having that layout.

## 5. Architecture

```text
Watch (Wear OS)                           Phone (patched Android client)
┌─────────────────────────┐ Data Layer    ┌────────────────────────────────┐
│ Compose for Wear OS UI  │ command ────▶ │ WearBridgeService              │
│ Tile                    │ ◀──── state  │   └─ BridgeController          │
│ WearMessageService      │              │       ├─ VPNService Binder     │
│ StateStore              │              │       ├─ connection observer   │
└─────────────────────────┘              │       └─ status / setup prompts│
                                        └──────────────┬─────────────────┘
                                                       │
                                        ┌──────────────▼─────────────────┐
                                        │ VPNService → NetBird Go engine │
                                        └────────────────────────────────┘
```

### Wearable Data Layer protocol

**Both apps must use `applicationId = "io.netbird.client"` and the same signing
certificate.** Google Play services scopes Data Layer delivery to matching package
and certificate identities; an unrelated app on a paired watch does not thereby
obtain this project's command channel.

- Phone capability: `netbird_wear_bridge`.
- Watch → phone: `/netbird/command`, with JSON payload:
  ```json
  {"command":"connect|disconnect|toggle|status","requestId":"<uuid>"}
  ```
- Phone → watch: `/netbird/state`, with JSON payload, for example:
  ```json
  {"state":"CONNECTED","message":null,"deviceName":"Phone","requestId":"<uuid>","timestamp":1700000000000}
  ```
- The phone publishes status in response to requests and engine state changes.
  `requestId` correlates a command response with its request; timestamps are epoch
  milliseconds.

Watch-visible states are `CONNECTED`, `CONNECTING`, `DISCONNECTING`,
`DISCONNECTED`, `NEEDS_LOGIN`, `NEEDS_SETUP`, `NO_NETWORK`, and `ERROR`.
`PHONE_UNAVAILABLE` is a watch-side state for discovery or response failures.
`NEEDS_SETUP` includes missing VPN consent or a blocked background connection start.

### Phone bridge

The patch adds `WearBridgeService`, `BridgeController`, `WearState`, and a
signature-protected `AutomationReceiver` in the Android app:

- Receive watch commands and bind to the existing `VPNService` in the same process.
- Call `runEngine(null, false)` or `stopEngine()`, following the existing Quick
  Settings Tile's engine-control path.
- Read status and subscribe through an independent connection observer, without
  replacing the listener used by the phone UI.
- Return setup status and a notification when phone-side action is required.
- Register the `netbird_wear_bridge` capability through the application.

The bridge and observer are event-driven. This is not a claim of zero battery
cost or of a process that can never be stopped by Android. See
[the patch guide](NETBIRD_PATCH.md) for files and lifecycle details.

### Watch app

- Kotlin and Compose for Wear OS Material 3; connection control, status, device
  name, refresh, and setup/error hints in a scrollable screen.
- `WearMessageService` receives status when the UI is closed, stores it, and
  requests a Tile update.
- `NetbirdTileService` shows status and a wide connection switch.
- `CapabilityClient` and `MessageClient` provide asynchronous discovery and
  commands with timeout/error handling.
- System-selected UI localization in English, Polish, German, Spanish, French,
  Portuguese, Italian, Japanese, Korean, and Simplified Chinese; English fallback.
  Documentation is English-only.

## 6. Security and permissions

- The watch receives status and sends control commands; it does not receive the
  phone's NetBird credentials or private keys through this protocol.
- Communication uses Google Play services' Wearable Data Layer and its matching
  package/certificate identity model. This is not an independent security audit.
- The additional broadcast API requires
  `io.netbird.client.permission.CONTROL_VPN` with protection level `signature`
  from 1.0.3 onward. Status-broadcast recipients also require that permission.
  Ordinary third-party apps, Tasker, and unprivileged ADB cannot use the API.
- The phone patch requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to support a
  user-approved background-start exemption. No AccessibilityService is used.
- Both APKs must be signed by the same key. Current sideload builds use a local
  debug/developer key, not NetBird's official signing certificate.
- Installing the phone APK replaces the official app. A certificate mismatch
  requires uninstalling first, which deletes the original app's local data.

See [the security policy](../SECURITY.md) before installation or redistribution.

## 7. Implementation status and acceptance criteria

The watch app, phone bridge, patch helper, and debug/release build scripts are
implemented. Historical automated build and test results, earlier physical-device
results, and remaining manual checks are recorded in [the test plan](TESTING.md).
They are **not** a claim that the latest public APK pair has been physically tested.

Acceptance criteria for a candidate release:

1. Both apps build successfully from the pinned source, including
   `:wear:assembleDebug` and phone `:app:assembleDebug`.
2. Watch protocol/UI logic tests and phone observer-regression tests pass.
3. The patch applies to a clean pinned upstream tree; reapplying it is harmless,
   and an incompatible tree is refused without resetting or deleting changes.
4. Both APKs are installable, have matching package/signature identities, and
   expose only the intended signature-protected automation API.
5. The documented installation and setup procedure works on supported hardware.
6. Watch controls, Tile rendering, foreground phone-UI updates, locked-phone
   operation, timeouts, and permission denial are verified on the candidate APKs.
7. Documentation clearly states the unofficial status, replacement requirement,
   licensing, security boundaries, and limits of test evidence.

The intended fork publication remains a separate step from this standalone
implementation; it must retain the pinned base and use the layout in section 4.

## 8. Risks and outstanding checks

- **Go/NDK compatibility:** the AAR build pins NDK r23b (23.1.7779620) and selects
  `gomobile`/`gobind` from the pinned engine's module dependencies. Toolchain pins
  improve repeatability but do not establish byte-for-byte reproducible APKs.
- **Background foreground-service starts on Android 12+:** a watch command arrives
  while the phone app may be in the background. Connecting starts a foreground
  service and needs an applicable exemption. The bridge handles denied starts by
  returning `NEEDS_SETUP` and offering a battery-optimization exemption prompt.
  Manufacturer restrictions still need device testing. Disconnecting does not
  require starting a new foreground service.
- **Upstream changes:** API, manifest, or build changes may require patch updates.
- **Stale status and sleeping devices:** Data Layer latency, stopped processes,
  and unreachable devices can leave cached watch status until a fresh response.
- **Watch layout and localization:** long labels, error text, and translated
  strings must remain usable on a round display and in the Tile.
- **Physical validation:** earlier device evidence covers specific earlier builds.
  Public 1.0.3 still requires physical regression testing, including its protected API.

## 9. Installation summary

1. Record required phone settings, uninstall the official client if its certificate
   differs, and install the phone APK from [dist/](../dist/).
2. Open the phone app, configure/sign in, connect once, and approve VPN consent.
3. Exempt NetBird from battery optimization for background connection control.
4. Install the matching watch APK through wireless ADB; remove the obsolete
   `io.netbird.wear` package only if it was previously installed.
5. Open NetBird on the watch, add the NetBird VPN Tile, and run the manual checks.

For exact commands and warnings, use [the installation guide](../README.md#installation-and-first-run).
