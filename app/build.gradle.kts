import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.let { load(FileInputStream(it)) }
}

val signRelease = project.hasProperty("signApk") && project.property("signApk") == "true"

fun getProp(key: String, envKey: String): String = localProperties.getProperty(key) ?: project.findProperty(key)?.toString() ?: System.getenv(envKey) ?: throw GradleException("$key not found")
fun cleanDir(dir: File, label: String) { if (dir.exists()) { dir.deleteRecursively(); println("deleted $label") } }

android {
    namespace = "com.je.dejpeg"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.je.dejpeg"
        minSdk = 24
        targetSdk = 36
        versionCode = 352
        versionName = "3.5.2"
    }
    signingConfigs{
        if (signRelease) create("release") {
            storeFile = file(getProp("keystore.path", "KEYSTORE_PATH"))
            storePassword = getProp("keystore.password", "KEYSTORE_PASSWORD")
            keyAlias = getProp("keystore.alias", "KEYSTORE_ALIAS")
            keyPassword = getProp("keystore.keyPassword", "KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true; isShrinkResources = true; isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signRelease) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true;
            applicationIdSuffix = ".debug";
            versionNameSuffix = "-debug"
        }
    }
    splits.abi { isEnable = true; reset(); include(project.findProperty("targetAbi")?.toString() ?: "arm64-v8a"); isUniversalApk = false }
    applicationVariants.all { outputs.all {
        val abiFilter = filters.find { it.filterType == "ABI" }?.identifier
        val debugSuffix = if (name.contains("debug", true)) "-debug" else ""
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = listOfNotNull("dejpeg", abiFilter).joinToString("-") + "$debugSuffix.apk"
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += listOf("DebugProbesKt.bin", "kotlin-tooling-metadata.json")
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

tasks.register("cleanApks") { group = "build"; doFirst { cleanDir(file("${rootProject.rootDir}/apks"), "apks") } }

if (signRelease) tasks.register("moveApks") {
    group = "build"
    doLast {
        val dest = file("${rootProject.rootDir}/apks").apply { mkdirs() }
        file("${layout.buildDirectory.get()}/outputs/apk").walkTopDown().filter { it.isFile && it.extension == "apk" }.forEach { it.copyTo(dest.resolve(it.name), overwrite = true) }
        println("moved apks to $dest")
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    if (!name.contains("debug", true)) { dependsOn("cleanApks"); if (signRelease) finalizedBy("moveApks") }
}
