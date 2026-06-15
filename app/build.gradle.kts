@file:Suppress("SpellCheckingInspection")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val propsFile = file("../local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}
val releaseStoreFile: String? = localProperties.getProperty("keystore.path")
val releaseStorePassword: String? = localProperties.getProperty("keystore.password")
val releaseKeyAlias: String? = localProperties.getProperty("keystore.alias")
val releaseKeyPassword: String? = localProperties.getProperty("keystore.keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() } && releaseStoreFile?.let { file(it).exists() } == true

val buildOidn = gradle.startParameter.taskNames.any { "oidn" in it.lowercase() }

android {
    namespace = "com.je.dejpeg"
    ndkVersion = "29.0.14206865"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "com.je.dejpeg.litert"
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 410
        versionName = "4.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
        buildConfigField("boolean", "OIDN_ENABLED", "false")
        applicationVariants.all {
            val variant = this
            val abi = ndk.abiFilters.first()
            val signingState = if (hasReleaseSigning) "" else "-unsigned"
            val fileName = "${rootProject.name.lowercase()}-$abi$signingState"
            variant.outputs.all {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "$fileName.apk"
            }
        }
    }
    if (buildOidn) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "4.1.2"
            }
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = ".debug"
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
        create("oidnDebug") {
            initWith(getByName("debug"))
            buildConfigField("boolean", "OIDN_ENABLED", "true")
        }
        create("oidnRelease") {
            initWith(getByName("release"))
            buildConfigField("boolean", "OIDN_ENABLED", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += listOf(
            "DebugProbesKt.bin", "kotlin-tooling-metadata.json"
        )
    }
    lint {
        disable += "IconXmlAndPng"
    }
    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.core.ktx)
    implementation(libs.google.material)
    implementation(libs.onnxruntime.android)
    implementation(libs.zoomable)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.backdrop)
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.google.ai.edge.litert:litert-gpu:1.4.2")
}
