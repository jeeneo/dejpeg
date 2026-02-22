import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val propsFile = file("../local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}
val releaseStoreFile = localProperties.getProperty("keystore.path")
val releaseStorePassword = localProperties.getProperty("keystore.password")
val releaseKeyAlias = localProperties.getProperty("keystore.alias")
val releaseKeyPassword = localProperties.getProperty("keystore.keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

val buildOdin = project.findProperty("buildOdin")?.toString()?.toBoolean() ?: false

android {
    namespace = "com.je.dejpeg"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.je.dejpeg"
        minSdk = 24
        targetSdk = 36
        versionCode = 360
        versionName = "3.6.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
        buildConfigField("boolean", "ODIN_ENABLED", buildOdin.toString())
    }

    if (buildOdin) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir(if (buildOdin) "src/odin/java" else "src/noOdin/java")
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
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json"
        )
    }
    bundle {
        abi {
            enableSplit = true
        }
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
    implementation(libs.google.material)
    implementation(libs.onnxruntime.android)
    implementation(libs.zoomable)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
}

androidComponents {
    onVariants { variant ->
        val abis = android.defaultConfig.ndk.abiFilters
        val abi = if (abis.size == 1) abis.first() else "universal"
        val taskName = "assemble${variant.name.replaceFirstChar { it.uppercase() }}"
        tasks.matching { it.name == taskName }.configureEach {
            doLast {
                val outDir = layout.buildDirectory.dir("outputs/apk/${variant.name}").get().asFile
                outDir.listFiles()
                    ?.filter { it.extension == "apk" }
                    ?.forEach { it.renameTo(File(outDir, "dejpeg-${abi}.apk")) }
            }
        }
    }
}
