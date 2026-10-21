// Halo Model Lab — root build file.
// AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android is intentionally absent.
// Build-version consistency is NOT verified in HML-L0; see docs/PENDING_VERIFICATION.md.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
