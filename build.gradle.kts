// Top-level build file for BillBook.
// The kapt plugin version is declared EXPLICITLY and exactly once, in
// app/build.gradle.kts (1.9.22, matching the Kotlin version) — the GitHub
// Actions APK build fails if kapt is applied without a version, and
// declaring it in two places caused duplicate-plugin confusion before.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
