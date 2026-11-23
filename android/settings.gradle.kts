// ZT Keyboard is a Flutter project whose Android module is the keyboard itself
// (HeliBoard). The Flutter loader below adds the Flutter plugins as Gradle projects;
// the Offline edition (-PztOffline) leaves them out (see app/build.gradle.kts).
pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            val file = file("local.properties")
            require(file.exists()) {
                "android/local.properties is missing. Run `flutter build apk --config-only --flavor full` once."
            }
            file.inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0" apply false
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}

include(":app")
// whisper.cpp for voice typing, native code only.
include(":whisper")
// llama.cpp for writing help (finish my sentence, proofread), native code only.
include(":llm")
if (!providers.gradleProperty("ztOffline").isPresent) {
    apply(plugin = "dev.flutter.flutter-plugin-loader")
}
// tools/make-emoji-keys is HeliBoard's developer tool for regenerating the emoji
// keys. It is kept in the tree but left out of the build; include it here when needed.
