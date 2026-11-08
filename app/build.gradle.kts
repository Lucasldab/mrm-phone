import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.lldab.mrmphone"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.lldab.mrmphone"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Release signing with nocturne's key (same key, so one Ventoy-stick
    // backup covers both apps). Resolved from the environment so the same
    // build works locally and in CI:
    //   MRM_PHONE_KEYSTORE           path to the .jks (default ~/keystore/nocturne-release.jks)
    //   MRM_PHONE_KEYSTORE_PASSWORD  store password
    //   MRM_PHONE_KEY_ALIAS          default "nocturne"
    //   MRM_PHONE_KEY_PASSWORD       defaults to the store password
    // Without them the release variant stays unsigned and `assembleDebug`
    // on a fresh clone still works.
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("MRM_PHONE_KEYSTORE")
                ?: "${System.getProperty("user.home")}/keystore/nocturne-release.jks"
            val ksPass = System.getenv("MRM_PHONE_KEYSTORE_PASSWORD")
            if (ksPass != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = System.getenv("MRM_PHONE_KEY_ALIAS") ?: "nocturne"
                keyPassword = System.getenv("MRM_PHONE_KEY_PASSWORD") ?: ksPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            signingConfigs.getByName("release").storeFile?.let {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 off, as in nocturne: an untested minified build would go
            // straight to the phone through Obtainium.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        lintConfig = file("$rootDir/lint.xml")
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

base {
    archivesName.set("mrm-phone")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
}
