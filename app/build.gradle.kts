plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Iceraven/Fenix: ABI APK splits + compressed JNI only apply to APK tasks.
// `bundleRelease` must produce a Play-style AAB (Play splits ABIs on delivery).
val isAppBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }

android {
    namespace = "holocore.browser.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "holocore.browser.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "1.9"

        vectorDrawables {
            useSupportLibrary = true
        }

        // AGP forbids ndk.abiFilters + splits.abi at the same time.
        // Splits handle APKs; abiFilters only apply when building an AAB
        // (splits are disabled for bundle tasks).
        if (isAppBundle) {
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    // GeckoView bundles native libraries (libxul.so etc.) for every ABI, which balloons
    // a universal APK to 200MB+. Nearly all Android devices in active use are arm64-v8a,
    // so we ship that alone and drop armeabi-v7a/x86/x86_64 -- this is what brings the
    // signed release APK down from ~256MB to a realistic size. Re-add other ABIs here
    // (or switch this workflow to publish an AAB) if 32-bit/emulator support is needed.
    splits {
        abi {
            isEnable = !isAppBundle
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    bundle {
        abi {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        language {
            // Keep strings in the base module so in-app language can work later.
            enableSplit = false
        }
    }

    // Iceraven/Fenix: do not compress omni.ja. GeckoView must unpack a compressed
    // omni.ja before it can start, which delays first paint.
    androidResources {
        noCompress += "ja"
    }

    signingConfigs {

        getByName("debug") {
            val keystorePath = System.getenv("ANDROID_DEBUG_KEYSTORE")
            storeFile = file(keystorePath ?: "${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = System.getenv("ANDROID_DEBUG_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("ANDROID_DEBUG_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("ANDROID_DEBUG_KEY_PASSWORD") ?: "android"
        }

        create("release") {
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }

        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Iceraven/Fenix packaging:
    // - Drop unused JNI ABIs from third-party deps
    // - useLegacyPackaging compresses .so files inside the APK (libxul.so etc.).
    //   AGP 8+ defaults to uncompressed aligned libs so the APK can mmap them;
    //   that makes the *file* ~2x larger. Legacy packaging is why Iceraven's
    //   arm64 APK is ~90MB instead of ~190MB. Device still extracts libs on install.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
        }
        jniLibs {
            excludes += listOf(
                "**/armeabi/*.so",
                "**/armeabi-v7a/*.so",
                "**/mips/*.so",
                "**/mips64/*.so",
                "**/x86/*.so",
                "**/x86_64/*.so",
            )
            // Compress .so files in APKs (smaller file). Leave uncompressed
            // in AABs so Play can serve aligned native libs per device.
            useLegacyPackaging = !isAppBundle
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.21")
    }
}

dependencies {
    implementation(libs.geckoview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
}
