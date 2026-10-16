// Root build configuration. Keep explicit versions here: Gradle cannot
// resolve a versionless Android application plugin from repositories.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}
