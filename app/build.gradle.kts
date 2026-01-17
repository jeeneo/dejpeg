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
        buildConfigField("boolean", "BRISQUE_ENABLED", "true")
        // what is semantic versioning? libraries? you think i know? i just bump these numbers every now and then :(
        // it's the 3rd major release, with 3 minor updates, and this is the Nth(?) patch - random numbers fr
        // "adds features without breaking things" LOL i break things every update wha?
        versionCode = 343
        versionName = "3.4.3"
    }
    signingConfigs {
        if (project.hasProperty("signApk") && project.property("signApk") == "true") {
            create("release") {
                storeFile = file(project.findProperty("keystore.path")?.toString() ?: System.getenv("KEYSTORE_PATH")!!)
                storePassword = project.findProperty("keystore.password")?.toString() ?: System.getenv("KEYSTORE_PASSWORD")!!
                keyAlias = project.findProperty("keystore.alias")?.toString() ?: System.getenv("KEYSTORE_ALIAS")!!
                keyPassword = project.findProperty("keystore.keyPassword")?.toString() ?: System.getenv("KEY_PASSWORD")!!
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
    }
    applicationVariants.all {
        outputs.all {
            val appName = "dejpeg"
            val variant = this.name
            val abiFilter = filters.find { it.filterType == "ABI" }?.identifier
            val debugSuffix = if (variant.contains("debug", ignoreCase = true)) "-debug" else ""
            val apkName = if (abiFilter != null) "$appName-$abiFilter$debugSuffix.apk" else "$appName$debugSuffix.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = apkName
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
        buildConfig = true
    }
    flavorDimensions += "variant"
    productFlavors {
        create("full") {
            dimension = "variant"
            applicationIdSuffix = ".opencv"
            buildConfigField("boolean", "BRISQUE_ENABLED", "true")
            versionNameSuffix = "-opencv"
        }
        create("lite") {
            dimension = "variant"
            buildConfigField("boolean", "BRISQUE_ENABLED", "false")
        }
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
        getByName("full") {
            java.srcDir("src/full/java")
            jniLibs.srcDir("src/full/jniLibs")
        }
        getByName("lite") {
            jniLibs.srcDir("src/lite/jniLibs")
            java.srcDir("src/lite/java")
        }
    }
    packaging {
        jniLibs {
            pickFirsts += "lib/*/libonnxruntime.so"
            keepDebugSymbols += "**/*.so" // they're already stripped, just stop warning
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

tasks.register<Exec>("buildLibs") {
    group = "build"
    description = "build native libraries"
    onlyIf { !project.hasProperty("skipBuildLibs") }
    val buildScript = file("${rootProject.rootDir}/build.sh")
    commandLine("bash", buildScript.absolutePath, "--no-cleanup", "--skip-gradle", "--debug", "--no-upx")
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

tasks.matching { it.name.startsWith("assemble") && it.name.contains("debug", ignoreCase = true) }.configureEach {
    dependsOn("buildLibs")
}

tasks.matching { it.name.startsWith("assemble") && !it.name.contains("debug", ignoreCase = true) }.configureEach {
    dependsOn("cleandir")
    if (project.hasProperty("signApk") && project.property("signApk") == "true") {
        finalizedBy("move")
    }
}
