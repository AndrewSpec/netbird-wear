# Test plan and verification record

This document separates **historical automated checks**, **historical physical
device evidence**, and **checks still required for a release candidate**.
Recorded results apply to the versions named below; they are not evidence that
all checks have been rerun after every source or documentation change.

**The public 1.0.3 APK pair has not been physically tested.** Earlier successful
Galaxy Watch 4 tests must not be presented as certification of that public build.
UI localization changes also require their own build and device validation.

Paths below are relative to the current standalone project. The planned fork
will put the Android client at the repository root and this watch project,
documentation, and scripts under `wear-os/`, with one customization commit above
Android upstream `ad7865a2c3cc1bc940bf63512ccab1819ebf53a6`. That relocation is
separate from the tests recorded here.

## 1. Historical automated verification

The project previously recorded the following successful checks:

- Watch `:wear:assembleDebug` and `:wear:testDebugUnitTest` for protocol codecs,
  states, and commands.
- Patched Android client `:app:assembleDebug`.
- Phone APK inspection: package `io.netbird.client`, `minSdk 26`,
  `WearBridgeService` and `AutomationReceiver` declarations, ARM64 native library
  `lib/arm64-v8a/libgojni.so`, and a debug/developer signing certificate.
- [netbird-wear.patch](../patches/netbird-wear.patch) applies to clean upstream
  commit `ad7865a2c3cc1bc940bf63512ccab1819ebf53a6` (`git apply --check`).
- Go AAR generation from the pinned `netbird` submodule using NDK r23b and Go 1.26+.
- [build-release.sh](../scripts/build-release.sh) builds signed phone and watch
  release variants; `apksigner verify` accepts both APKs.
- Artifact hashes were recorded in `dist/SHA256SUMS` for the corresponding outputs.
  Such a file must be regenerated for new artifacts; it is not proof of a later build.

### Version 1.0.1

- Watch `:wear:testDebugUnitTest`: 8 passing protocol tests.
- Phone `:tool:testDebugUnitTest`: 66 passing tests, including 3
  `ConnectionObserversUnitTest` regressions for UI/bridge observer fan-out.
- Both apps built in debug and release variants.

### Watch version 1.0.2

- `:wear:testDebugUnitTest`: 13 passing tests (8 protocol tests and 5 switch tests).
- Switch tests cover track color, thumb side, transitional states, click target,
  and accessibility description. These logic tests do not replace device rendering
  or accessibility-service tests.
- `:wear:assembleDebug` and `:wear:assembleRelease`: successful.
- Recorded version metadata: `versionCode=3`, `versionName=1.0.2`; signing
  certificate unchanged and APK hashes checked.
- This version was not installed during that session: no ADB devices or mDNS
  advertisements were available. The new Tile still needed visual device review.

### Public version 1.0.3 preparation (2026-10-18)

- Phone `:tool:testDebugUnitTest` and `:app:assembleRelease`: successful.
- Watch `:wear:testDebugUnitTest` and `:wear:assembleRelease`: successful.
- Compiled phone manifest: the receiver requires
  `io.netbird.client.permission.CONTROL_VPN`, whose definition has
  `protectionLevel=signature` (`0x2`).
- Patch applied to a clean pinned base; reapplication was idempotent.
  Applying to an incompatible older patched clone was refused without deleting
  or resetting its changes.
- **No physical test of 1.0.3 was performed.** Earlier device tests cover earlier
  versions; 1.0.3 adds automation-API protection that still needs a device regression test.

## 2. Historical physical-device evidence

### Galaxy Watch 4 + Galaxy S24 Ultra (2026-10-17)

The user confirmed watch-driven VPN control after the phone and watch package
names and signing certificates were aligned. A subsequently reported failure to
refresh the open phone screen was addressed in phone version 1.0.1-wear.

An ADB-assisted check on those devices with phone version 1.0.1-wear recorded:

- With `MainActivity` continuously open, the watch's `connect` command was followed
  by `HomeFragment` rendering `Connecting…` and then `Connected`.
- A watch `disconnect` command was followed by `Disconnecting…` and `Disconnected`.
  A UI dump confirmed `Disconnected` and `checked=false`.
- The phone screen was not restarted or reopened between commands.
- The watch's NetBird logo and orange control were visually confirmed, and the
  watch received `CONNECTED` after updating.
- The watch screen was scrollable so refresh controls and longer messages could
  remain accessible on the round display.

These observations do not establish that the later 1.0.2 Tile, 1.0.3 security
changes, or newly added translations were physically tested.

## 3. Automated checks for each new candidate

Run from this standalone project's root, using the environment described in
[the build guide](../README.md#build):

```bash
./scripts/build-all.sh
./gradlew :wear:testDebugUnitTest
```

In the patched Android clone, also run:

```bash
./gradlew :tool:testDebugUnitTest
```

For each candidate, record the exact source revision, APK version metadata,
build/test results, APK hashes, and signing-certificate fingerprints. Verify:

- [ ] Patch applies to the clean pinned upstream base and reapplication is harmless.
- [ ] Incompatible trees are refused without losing unrelated work.
- [ ] Phone and watch APKs both use `io.netbird.client` and have the same certificate.
- [ ] APK signatures verify; both debug and release variants build as intended.
- [ ] The phone manifest declares signature-level `CONTROL_VPN` and requires it
      on the automation receiver.
- [ ] The phone APK contains the intended ARM64 native library and SDK metadata.
- [ ] Watch protocol, switch, and any localization tests pass.
- [ ] Translated resources are valid, retain formatting placeholders, and build
      without missing-resource errors.

A successful build, manifest inspection, or unit test is not a physical test or
an independent security audit.

## 4. Manual device checklist

Use a supported Wear OS watch and Android phone. Record device models, OS
versions, APK hashes/version metadata, and which checks actually passed.

### Installation and setup

1. Record the phone configuration needed for recovery. If the official app uses
   a different certificate, uninstall it first. **Uninstalling deletes local
   configuration.** Install `dist/netbird-wear-release.apk` on the phone.
2. Open NetBird, sign in or add a profile, connect manually once, and approve the
   system VPN consent prompt.
3. Exempt NetBird from battery optimization, typically under Settings → Apps →
   NetBird → Battery → Unrestricted. Device wording may differ.
4. Install `dist/netbird-watch-release.apk` through wireless ADB. Remove the old
   `io.netbird.wear` package only if it exists. Verify that the installed phone
   and watch apps both use `io.netbird.client` and the same certificate.

### App controls and phone-UI synchronization

- [ ] Open the watch app and confirm the initial status matches the phone after
      a fresh response, rather than assuming a cached status is current.
- [ ] Tap **Connect**: the phone starts its VPN and the watch reaches `CONNECTED`.
- [ ] Keep the phone app open: its status and switch show Connecting → Connected
      without reopening the screen.
- [ ] Tap **Disconnect**: both screens promptly show Disconnecting → Disconnected.
- [ ] Leave the phone UI and return; subsequent commands still update both screens.
- [ ] Change the connection from the phone and verify the watch receives the update.
- [ ] Check refresh, scrollability, long setup/error text, and accessibility labels.

### Tile, localization, and background operation

- [ ] Add **NetBird VPN** from the watch's Tile list; confirm status and controls render.
- [ ] Confirm disconnected styling: gray track and white thumb on the left.
- [ ] Confirm connected styling: orange track and white thumb on the right.
- [ ] Confirm the action label remains readable and that a tap follows the intended
      app-opening/command flow; check transitional states and long error messages.
- [ ] With the phone locked and screen off, connect and disconnect after granting
      the battery-optimization exemption. Verify the actual phone VPN, not only
      the watch's displayed state.
- [ ] Repeat without the exemption and confirm a blocked background connection
      returns a useful setup state/notification rather than falsely claiming success.
- [ ] Remove phone reachability and confirm the watch reports **Phone unavailable**
      after the timeout; restore connectivity and confirm recovery.
- [ ] Repeat after the watch and phone have been idle to measure Data Layer latency.
- [ ] Change the watch system locale through `en`, `pl`, `de`, `es`, `fr`, `pt`,
      `it`, `ja`, `ko`, and `zh-CN`; check both activity and Tile text, action labels,
      status, accessibility descriptions, placeholders, clipping, and scrolling.
- [ ] Select an unsupported system locale and verify the English fallback. Confirm
      that locale selection is automatic rather than an in-app setting.

### Security and authentication

- [ ] From unprivileged ADB or an unrelated app, send an explicit automation
      broadcast to `io.netbird.client/.wear.AutomationReceiver`. It must be denied
      because the sender lacks signature-level `CONTROL_VPN`; the VPN must not change.
- [ ] Confirm ordinary apps without `CONTROL_VPN` cannot receive protected status
      broadcasts. Do not weaken the manifest permission to perform the test.
- [ ] Confirm the correctly signed watch can still control the VPN through the
      Wearable Data Layer after the broadcast-denial test.
- [ ] Confirm a mismatched package/certificate is not treated as a working Data
      Layer peer; perform negative tests in a disposable setup, not by losing the
      only copy of a production signing key.
- [ ] Expire or invalidate the NetBird session and confirm **Login required**.
- [ ] Test missing VPN consent/profile setup and confirm clear phone-side instructions.

## 5. Remaining device-dependent risks

- Manufacturer-specific foreground-service restrictions, especially on newer
  Samsung/Android releases, despite a battery-optimization exemption.
- Tile rendering and hit targets on Wear OS and round displays; the project uses
  Tiles API 1.4.1, but compile success alone does not validate the result.
- Data Layer response time after idle, temporary disconnection, or process restart.
- Long translated strings and script/font rendering in all supported locales.
- The public 1.0.3 permission boundary and full watch/phone regression path on
  the actual release APKs.

For architecture and security context, see [the plan](PLAN.md),
[the patch guide](NETBIRD_PATCH.md), and [the security policy](../SECURITY.md).
