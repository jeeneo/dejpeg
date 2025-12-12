// ./gradlew clean assembleRelease -PsignApk=true

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.je.dejpeg"
    compileSdk {
        version = release(36)
    }
    defaultConfig {
        applicationId = "com.je.dejpeg"
        minSdk = 24
        targetSdk = 36
        versionCode = 310
        versionName = "3.1.0"
    }
    signingConfigs {
        if (project.hasProperty("signApk") && project.property("signApk") == "true") {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_PATH") ?: "")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEYSTORE_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (project.hasProperty("signApk") && project.property("signApk") == "true") {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            if (project.hasProperty("targetAbi")) {
                include(project.property("targetAbi") as String)
            } else {
                include("arm64-v8a")
            }
            isUniversalApk = false
        }
        applicationVariants.all {
            outputs.all {
                val output = this
                val appName = "dejpeg"
                val abiFilter = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI.name }?.identifier
                val variantName = name
                val debugSuffix = if (variantName.contains("debug", ignoreCase = true)) "-debug" else ""
                val apkName = if (abiFilter != null) "$appName-$abiFilter$debugSuffix.apk" else "$appName-universal$debugSuffix.apk"
                if (output is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                    output.outputFileName = apkName
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    if (System.getenv("BUILD_BRISQUE_JNI") == "ON") {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
    ndkVersion = "27.3.13750724"
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }
    packaging {
        jniLibs {
            pickFirsts += "lib/*/libonnxruntime.so"
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

tasks.register("cleandir") {
    group = "build"
    description = "clean ./apks directory before build."
    doFirst {
        val destDir = file("${rootProject.rootDir}/apks")
        if (destDir.exists()) {
            destDir.deleteRecursively()
            println("deleted $destDir")
        }
    }
}

if (project.hasProperty("signApk") && project.property("signApk") == "true") {
    tasks.register("move") {
        group = "build"
        description = "move apks to the root ./apks directory."
        doLast {
            val apkDir = file("${layout.buildDirectory.get()}/outputs/apk")
            val destDir = file("${rootProject.rootDir}/apks")
            if (!destDir.exists()) destDir.mkdirs()
            apkDir.walkTopDown().filter { it.isFile && it.extension == "apk" }.forEach { apk ->
                apk.copyTo(destDir.resolve(apk.name), overwrite = true)
            }
            println("moved apks to $destDir")
        }
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    dependsOn("cleandir")
    if (project.hasProperty("signApk") && project.property("signApk") == "true") {
        finalizedBy("move")
    }
}