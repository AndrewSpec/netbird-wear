# Third-party notices and source provenance

This is an **unofficial** Wear OS companion project, not a NetBird GmbH release.
It replaces the official Android application with a modified GPL-licensed build.

## Upstream sources

- Android client: <https://github.com/netbirdio/android-client>
  - Base: `ad7865a2c3cc1bc940bf63512ccab1819ebf53a6`
  - License: GNU GPL v3, preserved in `LICENSES/GPL-3.0.txt`.
  - Changes: Wear Data Layer service, connection observers, notification
    permission, restricted automation receiver, regression tests.
  - All local changes are in `patches/netbird-wear.patch`.
- Go engine: <https://github.com/netbirdio/netbird>
  - Submodule commit: `01185ba3536bcc037481578420d73cb7cee96094`
  - Client license: BSD-3-Clause; preserve `LICENSES/NetBird-BSD-3-Clause.txt`.
  - The upstream tree contains differently licensed server directories; their
    own license files remain in the source archive. This project builds the
    Android client, not a server distribution.
- NetBird bird artwork: Android client's `nb_logo_full.xml` and
  `ic_netbird_btn.png`, used under upstream GPL-3.0 terms. Trademark ownership
  is unchanged.
- Gradle wrapper: Gradle project, Apache-2.0.
- AndroidX/Compose, Kotlin, Google Play Services Wearable, Guava and Go modules:
  third-party dependencies under their own licenses. Embedded notices and
  upstream license files must not be removed.

## Binary release sources

Every binary release must include a `netbird-wear-*-sources.tar.gz` attachment
alongside the APKs, with project sources, the patched Android client, pinned Go
engine and a manifest of source commits and build instructions. The archive
also carries Go module source archives from the build cache, including their
license files; it does **not** contain keystores, credentials or device logs.
The GitHub-generated project-only source ZIP is not a substitute for that bundle.

External SDKs and build dependencies are fetched from their published repositories;
build instructions live in README.md. The source bundle and release attachments
must remain accessible for as long as the associated binary release is offered.

## Signing

Personal sideload builds use a locally generated key shared by phone and watch.
The key is not in Git or release archives. Public APKs use the same private local
key for this first release so existing installations can be updated, but its
Android certificate is a development certificate, not an official NetBird identity.
No warranty is provided. See SECURITY.md for limitations and reporting guidance.
