# Publishing and updating

Target repository: <https://github.com/AndrewSpec/netbird-wear>.

## Before a release

1. Keep the Android base commit pinned in `scripts/env.sh`; do not silently
   follow upstream main. Propose upstream updates in a branch, resolve conflicts,
   rebuild the AAR, and run phone/watch tests before merging.
2. Review `patches/netbird-wear.patch`, particularly service listeners,
   manifest exports and permissions. Never export VPN control without permission.
3. Increment version codes for changed APKs. Keep package and signing certificate
   equal across both APKs. Back up the signing key outside the repository.
4. Build the phone and watch with JDK 17 and run the test commands in TESTING.md.
5. Test on paired devices: foreground phone UI refresh, background connect,
   disconnect, reopen the app, tile layout, and unavailable-phone behavior.
6. Create a source archive and APK checksums with the release packaging script.
   Inspect the archive for keys and local data. Do not upload debug APKs.
7. Publish to a draft GitHub Release first; review assets, licensing and source
   provenance, then publish it. Do not overwrite an existing release tag.

The initial repository uses a patch against a pinned Android client. A separate
fork with an upstream-tracking branch can replace this later; the current CI
checks patch application, not automatic upstream merging or full VPN behavior.

## Local GitHub CLI authentication

The local CLI, if installed during setup, is `.toolchain/gh/bin/gh`.
Use an ignored local config directory:

```bash
GH_CONFIG_DIR="$PWD/.github-auth" .toolchain/gh/bin/gh auth login \
  --hostname github.com --git-protocol https --web
```

Never paste a token into an issue, chat, command line, Git file or release asset.
CI runs with read-only repository permissions and has no signing secrets.
Public releases are intentionally manual.
