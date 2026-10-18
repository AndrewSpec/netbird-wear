// Root build file for the NetBird Wear project (watch side).
// The phone side is a patched build of the upstream NetBird Android client,
// see patches/ and scripts/ for reproduction.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
