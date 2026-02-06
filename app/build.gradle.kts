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
val ndkVersion = "29.0.14206865"
val cmakeVersion = "3.22.1"
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

fun getProp(key: String, envKey: String): String = localProperties.getProperty(key) ?: project.findProperty(key)?.toString() ?: System.getenv(envKey) ?: throw GradleException("$key not found")
fun getTargetAbis(): List<String> = when (val abi = project.findProperty("targetAbi")?.toString()) { "all" -> supportedAbis; null, "" -> listOf("arm64-v8a"); else -> listOf(abi) }
fun cleanDir(dir: File, label: String) { if (dir.exists()) { dir.deleteRecursively(); println("deleted $label") } }

fun jniLibsDir(abi: String) = file("src/main/jniLibs/$abi")
val hasPrebuilt: Boolean by lazy { getTargetAbis().all { file("src/main/jniLibs/$it/libbrisque_jni.so").exists() } }
val hasApk: Boolean by lazy { rootProject.projectDir.listFiles { _, name -> name.matches(Regex("dejpeg-.*\\.apk")) }?.isNotEmpty() == true }
val skipOpenCV: Boolean by lazy {
    when {
        hasApk -> { println("found apk, skipping native build"); true }
        hasPrebuilt -> { println("found prebuilt libs, skipping native build"); true }
        else -> false
    }
}

android {
    namespace = "com.je.dejpeg"
    compileSdk = 36
    ndkVersion = this@Build_gradle.ndkVersion
    defaultConfig {
        applicationId = "com.je.dejpeg"
        minSdk = 24
        targetSdk = 36
        versionCode = 351
        versionName = "3.5.1"
        if (!skipOpenCV) externalNativeBuild { cmake { arguments("-DANDROID_STL=c++_static") } }
    }
    signingConfigs { if (signRelease) create("release") {
        storeFile = file(getProp("keystore.path", "KEYSTORE_PATH"))
        storePassword = getProp("keystore.password", "KEYSTORE_PASSWORD")
        keyAlias = getProp("keystore.alias", "KEYSTORE_ALIAS")
        keyPassword = getProp("keystore.keyPassword", "KEY_PASSWORD")
    } }
    buildTypes {
        release {
            isMinifyEnabled = true; isShrinkResources = true; isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signRelease) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true; applicationIdSuffix = ".debug"; versionNameSuffix = "-debug"
            if (!skipOpenCV) externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=Debug") } }
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
    if (!skipOpenCV) externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = cmakeVersion } }
    packaging {
        resources.excludes += listOf("DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        jniLibs { useLegacyPackaging = false; pickFirsts.add("lib/*/libbrisque_jni.so") }
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

tasks.register("extractLibrariesFromApk") {
    group = "native"
    onlyIf { !hasPrebuilt && hasApk }
    doLast {
        val apkFile = rootProject.projectDir.listFiles { _, name -> name.matches(Regex("dejpeg-.*\\.apk")) }?.firstOrNull() ?: return@doLast
        println("found apk: ${apkFile.name}, extracting...")
        val tempDir = file("${layout.buildDirectory.get()}/tmp/apk-extract").apply { deleteRecursively(); mkdirs() }
        try {
            copy { from(zipTree(apkFile)); into(tempDir) }
            var count = 0
            getTargetAbis().forEach { abi ->
                val src = tempDir.resolve("lib/$abi/libbrisque_jni.so")
                if (src.exists()) { src.copyTo(jniLibsDir(abi).apply { mkdirs() }.resolve("libbrisque_jni.so"), overwrite = true); println("extracted for $abi"); count++ }
                else println("warning: not found for $abi")
            }
            println(if (count > 0) "extracted $count ABI(s)" else "no libraries extracted")
        } finally { tempDir.deleteRecursively() }
    }
}

tasks.register("prepareOpencv") {
    group = "native"
    dependsOn("extractLibrariesFromApk")
    onlyIf { !skipOpenCV }
    doLast {
        val buildScript = rootProject.projectDir.resolve("build.sh")
        if (!buildScript.exists()) throw GradleException("build.sh not found")
        val targetAbi = project.findProperty("targetAbi")?.toString() ?: "arm64-v8a"
        println("running script for $targetAbi...")
        val result = project.exec {
            workingDir = rootProject.projectDir
            commandLine(buildScript.absolutePath, targetAbi)
            environment("BUILD_JNI", "0")
        }
        if (result.exitValue != 0) throw GradleException("build.sh failed with exit code ${result.exitValue}")
    }
}

tasks.register("cleanBuild") {
    group = "native"
    onlyIf { !skipOpenCV }
    doFirst {
        cleanDir(file("../build"), "root/build")
        cleanDir(file(".cxx"), "app/.cxx")
        cleanDir(layout.buildDirectory.get().asFile, "app/build")
        rootProject.file("opencv").listFiles { _, name -> name.startsWith("build_android_") }?.forEach {
            cleanDir(it, "${it.name}")
        }
    }
}

tasks.matching { it.name.startsWith("buildCMake") || it.name.startsWith("externalNativeBuild") }.configureEach {
    dependsOn("cleanBuild", "prepareOpencv")
}

tasks.register("cleanApks") { group = "build"; doFirst { cleanDir(file("${rootProject.rootDir}/apks"), "apks") } }
tasks.register("cleanJniLibs") { group = "build"; onlyIf { hasPrebuilt }; doFirst { cleanDir(file("src/main/jniLibs"), "jniLibs") } }

if (signRelease) tasks.register("moveApks") {
    group = "build"
    doLast {
        val dest = file("${rootProject.rootDir}/apks").apply { mkdirs() }
        file("${layout.buildDirectory.get()}/outputs/apk").walkTopDown().filter { it.isFile && it.extension == "apk" }.forEach { it.copyTo(dest.resolve(it.name), overwrite = true) }
        println("moved apks to $dest")
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    dependsOn("extractLibrariesFromApk")
    if (!skipOpenCV) dependsOn("cleanJniLibs", "prepareOpencv")
    if (!name.contains("debug", true)) { dependsOn("cleanApks"); if (signRelease) finalizedBy("moveApks") }
}
