import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    // Applied below, and only for the full edition.
    id("dev.flutter.flutter-gradle-plugin") apply false
}

// Two editions of ZT Keyboard are built from this module:
//
//   full     the keyboard plus the ZT app around it (Flutter + Zero-Trust-Core: optional
//            sign-in, app lock, shared settings). Built with
//            `flutter build apk --flavor full`.
//   offline  the keyboard alone: no Flutter, no account, no INTERNET permission. Built with
//            `./gradlew -PztOffline assembleOfflineRelease` from android/.
//
// The Flutter Gradle plugin cannot be left out of one product flavor only, so the edition is
// chosen by the `ztOffline` Gradle property and each build has exactly one flavor.
val ztOffline = providers.gradleProperty("ztOffline").isPresent
if (!ztOffline) {
    // Must come after the Android and Kotlin plugins.
    apply(plugin = "dev.flutter.flutter-gradle-plugin")
} else {
    // The Flutter tool writes its plugin registrant into src/main on every `flutter pub get`.
    // It is not tracked and the Offline edition has no Flutter classes to compile it against.
    delete("src/main/java/io/flutter")
}

// `flutter build --build-number` writes flutter.versionCode here.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// One version for both editions: the `version:` line of pubspec.yaml.
val ztVersionName = Regex("""^version:\s*([^+\s]+)""", RegexOption.MULTILINE)
    .find(rootProject.file("../pubspec.yaml").readText())!!.groupValues[1]
// CI passes ZT_BUILD_NUMBER (1000 + run number) so every build installs over the last one.
val ztVersionCode = (System.getenv("ZT_BUILD_NUMBER")
    ?: localProperties.getProperty("flutter.versionCode")
    ?: "1").toInt()

android {
    namespace = "helium314.keyboard.latin"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "dev.sagron.zerotrustkeyboard"
        // 28 for the full edition: the shared core keeps the biometric unlock key in the Keystore.
        minSdk = if (ztOffline) 21 else 28
        targetSdk = 37
        versionCode = ztVersionCode
        versionName = ztVersionName
        // HeliBoard's settings migrations (AppUpgrade.kt) are keyed to HeliBoard's own version codes,
        // not ours. Keep this equal to the upstream versionCode we are based on, and raise it when
        // taking a newer HeliBoard release.
        buildConfigField("int", "UPSTREAM_VERSION_CODE", "4201")
        // Never let users load a closed-source gesture library into the keyboard (HeliBoard's
        // `nouserlib` build type did this). Swipe typing will come from our own open decoder.
        buildConfigField("boolean", "ALLOW_USER_LIB", "false")
        ndk {
            abiFilters.clear()
            // Flutter has no 32-bit x86 engine, so the full edition leaves that one out.
            abiFilters.addAll(
                if (ztOffline) listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                else listOf("armeabi-v7a", "arm64-v8a", "x86_64")
            )
        }
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    flavorDimensions += "edition"
    productFlavors {
        if (ztOffline) {
            create("offline") {
                dimension = "edition"
                // Its own app, so it can sit next to the full edition and never updates into it.
                applicationIdSuffix = ".offline"
            }
        } else {
            create("full") {
                dimension = "edition"
            }
        }
    }

    // Release signing, same environment variables as the other Zero-Trust apps.
    // Without them (local builds) release builds are signed with the debug key.
    val ztKeystore = System.getenv("CM_BUILD_DIR")?.let { File(it, "codemagic.keystore") }
    signingConfigs {
        if (ztKeystore != null && ztKeystore.exists()) {
            create("release") {
                storeFile = ztKeystore
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
        }
        debug {
            isMinifyEnabled = false
            isJniDebuggable = false
            applicationIdSuffix = ".debug"
        }
        if (ztOffline) {
            // HeliBoard's variant for unit tests on CI: skips tests known to fail and the ones
            // that call the network. Only the offline edition has it, so the Flutter plugin
            // never has to map it to a Flutter build mode.
            create("runTests") {
                isMinifyEnabled = false
                isJniDebuggable = false
            }
        }
    }
    if (ztOffline) {
        // AGP 9 only creates unit test tasks for the tested build type.
        testBuildType = "runTests"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    externalNativeBuild {
        ndkBuild {
            path = File("src/main/jni/Android.mk")
        }
    }

    packaging {
        jniLibs {
            // shrinks APK by 3 MB, zipped size unchanged
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // see https://github.com/HeliBorg/HeliBoard/issues/477
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

if (!ztOffline) {
    // Same as `flutter { source = "../.." }`; written this way because the plugin is applied
    // conditionally, so Gradle generates no typed accessor for its extension.
    extensions.getByName("flutter").withGroovyBuilder {
        setProperty("source", "../..")
    }
}

dependencies {
    // voice typing engine (whisper.cpp), see ../whisper
    implementation(project(":whisper"))
    // writing help engine (llama.cpp), see ../llm
    implementation(project(":llm"))

    // androidx
    implementation("androidx.core:core-ktx:1.17.0") // 1.18.0 requires minSdk 23
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // compose
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2025.11.01")) // newer requires minSdk 23
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("sh.calvin.reorderable:reorderable:3.1.0") // for easier re-ordering
    implementation("com.github.skydoves:colorpicker-compose:1.1.3") // for user-defined colors, newer requires minSdk 23

    // test
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:runner:1.7.0")
    testImplementation("androidx.test:core:1.7.0")
}
