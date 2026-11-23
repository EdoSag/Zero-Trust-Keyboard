// The language model engine for writing help: llama.cpp (MIT) built for Android as
// libzt_llm.so. Native code only; the Kotlin side is in the app
// (dev.sagron.zerotrustkeyboard.lm). Everything runs on the device.
plugins {
    id("com.android.library")
}

val ztOffline = providers.gradleProperty("ztOffline").isPresent

android {
    namespace = "dev.sagron.zerotrustkeyboard.llm"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        minSdk = 21
        ndk {
            // Same list as the app, so nothing is compiled that no edition ships.
            abiFilters.addAll(
                if (ztOffline) listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                else listOf("armeabi-v7a", "arm64-v8a", "x86_64")
            )
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    buildTypes {
        if (ztOffline) {
            // The app's unit test variant (see app/build.gradle.kts).
            create("runTests")
        } else {
            // The Flutter plugin gives the app a "profile" build type.
            create("profile")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
