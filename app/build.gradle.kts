import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.let { load(FileInputStream(it)) }
}

fun getProp(key: String, envKey: String): String =
    localProperties.getProperty(key) ?: project.findProperty(key)?.toString() ?: System.getenv(envKey)
    ?: throw GradleException("$key not found in local.properties, project properties, or $envKey env var")

val signRelease = project.hasProperty("signApk") && project.property("signApk") == "true"

android {
    namespace = "com.je.dejpeg"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.je.dejpeg"
        minSdk = 24
        targetSdk = 36
        versionCode = 350
        versionName = "3.5.0"
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_static")
            }
        }
    }
    signingConfigs {
        if (signRelease) {
            create("release") {
                storeFile = file(getProp("keystore.path", "KEYSTORE_PATH"))
                storePassword = getProp("keystore.password", "KEYSTORE_PASSWORD")
                keyAlias = getProp("keystore.alias", "KEYSTORE_ALIAS")
                keyPassword = getProp("keystore.keyPassword", "KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signRelease) signingConfig = signingConfigs.getByName("release")
            externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=MinSizeRel") } }
        }
        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=Debug") } }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include(project.findProperty("targetAbi")?.toString() ?: "arm64-v8a")
            isUniversalApk = false
        }
    }
    applicationVariants.all {
        outputs.all {
            val abiFilter = filters.find { it.filterType == "ABI" }?.identifier
            val debugSuffix = if (name.contains("debug", true)) "-debug" else ""
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = listOfNotNull("dejpeg", abiFilter).joinToString("-") + "$debugSuffix.apk"
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.2.3"
        }
    }
    ndkVersion = "29.0.14206865"
    packaging {
        resources {
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
        jniLibs {
            useLegacyPackaging = false
            pickFirsts.add("lib/*/libbrisque_jni.so")
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

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val opencvDir = rootProject.projectDir.resolve("opencv")
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val cpuBaseline = mapOf("arm64-v8a" to "", "armeabi-v7a" to "NEON", "x86_64" to "SSE3", "x86" to "SSE2")
val cpuDispatch = mapOf("arm64-v8a" to "", "armeabi-v7a" to "", "x86_64" to "SSE4_2,AVX,AVX2", "x86" to "SSE4_2,AVX")
val ndkVersion = "29.0.14206865"

fun getTargetAbis(): List<String> {
    val targetAbi = project.findProperty("targetAbi")?.toString()
    return when {
        targetAbi == "all" -> supportedAbis
        !targetAbi.isNullOrEmpty() -> listOf(targetAbi)
        else -> listOf("arm64-v8a")
    }
}

fun opencvBuildDir(abi: String) = opencvDir.resolve("build_android_$abi")
fun opencvInstallDir(abi: String) = opencvBuildDir(abi).resolve("install/sdk/native")
fun isOpencvBuilt(abi: String) = opencvInstallDir(abi).resolve("staticlibs/$abi/libopencv_quality.a").exists()
fun makeProcess(workDir: File, vararg args: String) = providers.exec { workingDir = workDir; commandLine(*args) }.result.get()

fun jniLibsDir(abi: String) = file("src/main/jniLibs/$abi")
fun hasLib(abi: String) = jniLibsDir(abi).resolve("libbrisque_jni.so").exists()
fun hasPrebuilt() = getTargetAbis().all { hasLib(it) }

tasks.register("extractLibrariesFromApk") {
    group = "native"
    description = "Extracts libbrisque_jni.so from existing prebuilt if available"
    onlyIf { !hasPrebuilt() }
    doLast {
        val apkFiles = rootProject.projectDir.listFiles { _, name -> name.matches(Regex("dejpeg-.*\\.apk")) }
        if (apkFiles.isNullOrEmpty()) {
            return@doLast
        }
        val apkFile = apkFiles.first()
        println("found apk: ${apkFile.name}, extracting...")
        val tempDir = file("${layout.buildDirectory.get()}/tmp/apk-extract").apply { 
            deleteRecursively()
            mkdirs() 
        }
        try {
            copy {
                from(zipTree(apkFile))
                into(tempDir)
            }
            var extractedCount = 0
            getTargetAbis().forEach { abi ->
                val libSource = tempDir.resolve("lib/$abi/libbrisque_jni.so")
                val libDest = jniLibsDir(abi).apply { mkdirs() }.resolve("libbrisque_jni.so")
                if (libSource.exists()) {
                    libSource.copyTo(libDest, overwrite = true)
                    println("extracted libbrisque_jni.so for $abi")
                    extractedCount++
                } else {
                    println("warning: libbrisque_jni.so not found for $abi in ${apkFile.name}")
                }
            }
            if (extractedCount > 0) {
                println("extracted libraries for $extractedCount ABI(s)")
            } else {
                println("no libraries extracted from ${apkFile.name}")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

tasks.register("cloneOpencv") {
    group = "native"
    description = "Clone OpenCV and OpenCV contrib repositories"
    val opencvRepo = opencvDir.resolve("opencv")
    val contribRepo = opencvDir.resolve("opencv_contrib")
    onlyIf { !hasPrebuilt() && (!opencvRepo.resolve(".git").exists() || !contribRepo.resolve(".git").exists()) }
    doLast {
        opencvDir.mkdirs()
        if (!opencvRepo.resolve(".git").exists()) {
            opencvRepo.deleteRecursively()
            makeProcess(opencvDir, "git", "clone", "--depth", "1", "https://github.com/opencv/opencv.git")
        }
        if (!contribRepo.resolve(".git").exists()) {
            contribRepo.deleteRecursively()
            makeProcess(opencvDir, "git", "clone", "--depth", "1", "https://github.com/opencv/opencv_contrib.git")
        }
    }
}

fun getNdkDir(): File = File(localProperties.getProperty("sdk.dir") ?: throw GradleException("sdk.dir not found in local.properties")).resolve("ndk/$ndkVersion")

getTargetAbis().forEach { abi ->
    tasks.register("buildOpencv_$abi") {
        group = "native"
        description = "Build OpenCV static libraries for $abi"
        dependsOn("cloneOpencv")
        val buildDir = opencvBuildDir(abi)
        outputs.dir(opencvInstallDir(abi))
        onlyIf { !hasPrebuilt() && !isOpencvBuilt(abi) }
        doLast {
            val toolchain = getNdkDir().resolve("build/cmake/android.toolchain.cmake")
            buildDir.mkdirs()
            val cmakeArgs = buildList {
                add("cmake"); add("-Wno-deprecated")
                add("-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}")
                add("-DANDROID_USE_LEGACY_TOOLCHAIN_FILE=OFF")
                add("-DANDROID_ABI=$abi"); add("-DANDROID_PLATFORM=android-24"); add("-DANDROID_STL=c++_static")
                add("-DCMAKE_BUILD_TYPE=MinSizeRel"); add("-DCMAKE_INSTALL_PREFIX=${buildDir.absolutePath}/install")
                add("-DBUILD_SHARED_LIBS=OFF")
                add("-DOPENCV_EXTRA_MODULES_PATH=${opencvDir.resolve("opencv_contrib/modules").absolutePath}")
                add("-DBUILD_LIST=core,imgproc,imgcodecs,ml,quality")
                add("-DCPU_BASELINE=${cpuBaseline[abi] ?: ""}")
                cpuDispatch[abi]?.takeIf { it.isNotEmpty() }?.let { add("-DCPU_DISPATCH=$it") }
                listOf("TESTS", "PERF_TESTS", "ANDROID_EXAMPLES", "DOCS", "opencv_java", "opencv_python2",
                    "opencv_python3", "opencv_apps", "EXAMPLES", "PACKAGE", "FAT_JAVA_LIB", "JASPER",
                    "OPENEXR", "PROTOBUF", "JAVA", "OBJC", "ANDROID_PROJECTS").forEach { add("-DBUILD_$it=OFF") }
                add("-DBUILD_ZLIB=ON"); add("-DBUILD_PNG=ON"); add("-DBUILD_JPEG=ON")
                listOf("JPEG", "PNG").forEach { add("-DWITH_$it=ON") }
                listOf("TIFF", "WEBP", "OPENEXR", "JASPER", "OPENJPEG", "IMGCODEC_HDR", "IMGCODEC_SUNRASTER",
                    "IMGCODEC_PXM", "IMGCODEC_PFM", "IPP", "EIGEN", "TBB", "OPENCL", "CUDA", "OPENGL", "VTK",
                    "GTK", "QT", "GSTREAMER", "FFMPEG", "V4L", "1394", "ADE", "PROTOBUF", "QUIRC", "LAPACK",
                    "OBSENSOR", "ANDROID_MEDIANDK", "ITT").forEach { add("-DWITH_$it=OFF") }
                add("-DOPENCV_ENABLE_NONFREE=OFF"); add("-DOPENCV_GENERATE_PKGCONFIG=OFF"); add("-DENABLE_LTO=ON")
                val flags = "-ffunction-sections -fdata-sections -fvisibility=hidden -Os"
                add("-DCMAKE_CXX_FLAGS=$flags"); add("-DCMAKE_C_FLAGS=$flags")
                add(opencvDir.resolve("opencv").absolutePath)
            }
            providers.exec { workingDir = buildDir; commandLine(cmakeArgs) }.result.get()
            val makeResult = providers.exec {
                workingDir = buildDir
                commandLine("make", "-j${Runtime.getRuntime().availableProcessors()}")
                isIgnoreExitValue = true
            }.result.get()
            if (makeResult.exitValue != 0) makeProcess(buildDir, "make", "-j1")
            makeProcess(buildDir, "make", "install")
        }
    }
}

tasks.register("buildOpencv") {
    group = "native"
    description = "Build OpenCV static libraries for all target ABIs"
    onlyIf { !hasPrebuilt() }
    dependsOn(getTargetAbis().map { "buildOpencv_$it" })
}

fun cleanDir(dir: File, label: String) { if (dir.exists()) { dir.deleteRecursively(); println("deleted $label") } }

tasks.register("cleandir") {
    group = "build"
    description = "clean ./apks directory before build."
    doFirst { cleanDir(file("${rootProject.rootDir}/apks"), "apks") }
}

tasks.register("cleanJniLibs") {
    group = "build"
    description = "Clean jniLibs directory (libraries now built by CMake)"
    onlyIf { hasPrebuilt() }
    doFirst { cleanDir(file("src/main/jniLibs"), "jniLibs") }
}

if (signRelease) {
    tasks.register("move") {
        group = "build"
        description = "move apks to the root ./apks directory."
        doLast {
            val destDir = file("${rootProject.rootDir}/apks").apply { mkdirs() }
            file("${layout.buildDirectory.get()}/outputs/apk").walkTopDown()
                .filter { it.isFile && it.extension == "apk" }
                .forEach { it.copyTo(destDir.resolve(it.name), overwrite = true) }
            println("moved apks to $destDir")
        }
    }
}

tasks.whenTaskAdded {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) {
        dependsOn("extractLibrariesFromApk")
        onlyIf { !hasPrebuilt() }
        dependsOn("buildOpencv")
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    dependsOn("extractLibrariesFromApk")
    if (!hasPrebuilt()) {
        dependsOn("cleanJniLibs", "buildOpencv")
    }
    if (!name.contains("debug", true)) {
        dependsOn("cleandir")
        if (signRelease) finalizedBy("move")
    }
}
