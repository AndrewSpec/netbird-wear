# NetBird Wear v1.0.3

First public source release of an unofficial Wear OS companion for NetBird.
Not affiliated with or endorsed by NetBird GmbH.

## Downloads

- `netbird-watch-release.apk`: Wear OS 3+, tested by the owner on Galaxy Watch 4.
- `netbird-wear-release.apk`: modified Android NetBird client, ARM64, Android 8+.
  The stock Play Store app does not expose the control bridge and will not work.
- `netbird-wear-v1.0.3-sources.tar.gz`: project, exact Android base + patch,
  pinned NetBird engine and Go module source ZIPs; see its SOURCE-README.txt.
- `SHA256SUMS`, `PROVENANCE.json`, `LICENSE`, `NOTICE.md`, `SECURITY.md`.

## Features

- Connect/disconnect phone VPN from the watch over Wearable Data Layer.
- Watch status, original NetBird bird mark and orange styling.
- Tile with a wide orange/grey switch, rather than a truncated round text button.
- Phone screen updates while commands arrive from the watch; the bridge no
  longer replaces MainActivity's connection listener.
- Automation receiver and state broadcasts now require a signature permission.
  Ordinary third-party apps cannot use that API to toggle the VPN.

## Install and update

Install the phone APK on the phone and watch APK on the watch, using explicit
ADB device serials. Install both from the same release/signing identity.
For existing project installations use `adb install -r` to preserve app data.

The official NetBird app uses a different certificate: switching from it requires
uninstalling it first, which removes local app data. Note your server URL and be
ready to sign in again. Open the modified client, sign in, approve VPN and allow
background activity before controlling it from the watch.

APKs use a private local development signing key, not NetBird's official key.
They are personal sideload builds, not security-audited production software.
Debug APKs, credentials and signing keys are not included.

## Verification and limitations

- Watch protocol/toggle tests and phone observer tests run locally before packaging.
- The owner confirmed VPN control works on Galaxy Watch 4 + S24 Ultra; the phone
  UI synchronization fix was also verified through ADB in version 1.0.1.
- The owner accepted the redesigned tile in version 1.0.2.
- The public-build signature-permission change is verified in the compiled
  manifest; a fresh physical end-to-end test of v1.0.3 has not been performed.
- Initial CI checks watch tests/build, shell syntax and patch applicability. It
  is not an end-to-end phone/watch VPN test.

Read the included licensing and security notes before redistributing. Keep the
corresponding source attachment with these binaries when sharing the release.
