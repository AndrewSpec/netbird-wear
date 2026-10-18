# Security

This is an experimental, unofficial VPN control companion. It is not audited or
endorsed by NetBird. Use it only if you trust the maintainer and the build.

## Trust boundaries

- Phone and watch APKs must have the same package (`io.netbird.client`) and
  signing certificate. Google Play Services Wearable Data Layer authenticates
  that application identity; it is not an unauthenticated Internet command API.
- Release 1.0.3 restricts the optional Android broadcast receiver and state
  broadcasts with `io.netbird.client.permission.CONTROL_VPN` (`signature`).
  Ordinary apps, Tasker and unprivileged ADB broadcasts cannot use this API.
  Wear OS communication is unaffected.
- Earlier personal builds exposed automation broadcasts without a permission;
  they should not be redistributed. Upgrade the phone build before sharing APKs.
- The Android VPN permission, NetBird account login and background execution
  settings must be granted by the user on the phone.
- Sideload releases use a private local development signing key. Do not confuse
  the certificate with the official NetBird certificate. Keep a secure offline
  backup of your signing key; never commit or upload it.
- Debug APKs are for development, not public release downloads.

## Reporting

Do not open a public issue containing tokens, setup keys, network identifiers,
logs with private addresses, or a working exploit. Prefer GitHub private
vulnerability reporting if enabled for this repository. If it is not enabled,
ask the maintainer for a private contact without describing the vulnerability.
Issues in the upstream VPN implementation should follow NetBird's own policy:
<https://github.com/netbirdio/netbird/security/policy>.
