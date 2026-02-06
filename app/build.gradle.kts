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
val opencvDir = rootProject.projectDir.resolve("opencv")
val opencvCommits = mapOf(
    "opencv" to "52633170a7c3c427dbddd7836b13d46db1915e9e",
    "opencv_contrib" to "abaddbcddf27554137d2fc4f0f70df013cf31a65"
)
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val cpuBaseline = mapOf("arm64-v8a" to "", "armeabi-v7a" to "NEON", "x86_64" to "SSE3", "x86" to "SSE2")
val cpuDispatch = mapOf("arm64-v8a" to "", "armeabi-v7a" to "", "x86_64" to "SSE4_2,AVX,AVX2", "x86" to "SSE4_2,AVX")

fun getProp(key: String, envKey: String): String = localProperties.getProperty(key) ?: project.findProperty(key)?.toString() ?: System.getenv(envKey) ?: throw GradleException("$key not found")
fun getSdkDir(): File = File(localProperties.getProperty("sdk.dir") ?: project.findProperty("sdk.dir")?.toString() ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: throw GradleException("SDK not found"))
fun getNdkDir(): File = File(localProperties.getProperty("ndk.dir") ?: project.findProperty("ndk.dir")?.toString() ?: System.getenv("ANDROID_NDK_HOME") ?: System.getenv("ANDROID_NDK_ROOT") ?: getSdkDir().resolve("ndk/$ndkVersion").absolutePath)
fun getCMakeExecutable(): File = getSdkDir().resolve("cmake/$cmakeVersion/bin/cmake")
fun getTargetAbis(): List<String> = when (val abi = project.findProperty("targetAbi")?.toString()) { "all" -> supportedAbis; null, "" -> listOf("arm64-v8a"); else -> listOf(abi) }
fun cleanDir(dir: File, label: String) { if (dir.exists()) { dir.deleteRecursively(); println("deleted $label") } }
fun makeProcess(workDir: File, vararg args: String) = providers.exec { workingDir = workDir; commandLine(*args) }.result.get()

fun jniLibsDir(abi: String) = file("src/main/jniLibs/$abi")
fun opencvBuildDir(abi: String) = opencvDir.resolve("build_android_$abi")
val hasPrebuilt: Boolean by lazy { getTargetAbis().all { file("src/main/jniLibs/$it/libbrisque_jni.so").exists() } }
val hasApk: Boolean by lazy { rootProject.projectDir.listFiles { _, name -> name.matches(Regex("dejpeg-.*\\.apk")) }?.isNotEmpty() == true }
val skipOpenCV: Boolean by lazy {
    when {
        hasApk -> { println("found apk, skipping opencv"); true }
        hasPrebuilt -> { println("found prebuilt libs, skipping opencv"); true }
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
        externalNativeBuild { cmake { arguments("-DANDROID_STL=c++_static", "-DCMAKE_BUILD_RPATH_USE_ORIGIN=ON", "-DCMAKE_BUILD_TIMESTAMP=") } }
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
            externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=MinSizeRel", "-DCMAKE_BUILD_RPATH_USE_ORIGIN=ON", "-DCMAKE_BUILD_TIMESTAMP=") } }
        }
        debug {
            isDebuggable = true; applicationIdSuffix = ".debug"; versionNameSuffix = "-debug"
            externalNativeBuild { cmake { arguments("-DCMAKE_BUILD_TYPE=Debug", "-DCMAKE_BUILD_TIMESTAMP=") } }
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
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = cmakeVersion } }
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

tasks.register("cloneOpencv") {
    group = "native"
    val opencvRepo = opencvDir.resolve("opencv")
    val contribRepo = opencvDir.resolve("opencv_contrib")
    onlyIf { !skipOpenCV }
    doLast {
        opencvDir.mkdirs()
        listOf("opencv" to opencvRepo, "opencv_contrib" to contribRepo).forEach { (name, repo) ->
            if (!repo.resolve(".git").exists()) { 
                repo.deleteRecursively()
                makeProcess(opencvDir, "git", "clone", "--depth", "1", "https://github.com/opencv/$name.git") 
            } else {
                println("updating $name...")
                makeProcess(repo, "git", "pull", "--depth", "1")
            }
            val commit = opencvCommits[name] ?: throw GradleException("commit not defined for $name")
            println("checking out $name commit: $commit")
            makeProcess(repo, "git", "checkout", commit)
        }
    }
}

tasks.register("checkCMake") {
    group = "native"
    onlyIf { !skipOpenCV }
    doLast {
        val sdkDir = getSdkDir()
        val installed = sdkDir.resolve("cmake").takeIf { it.exists() }?.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        println("cmake versions: ${installed.joinToString(", ").ifEmpty { "none" }}")
        if (cmakeVersion !in installed) {
            println("installing cmake $cmakeVersion")
            val sdkmanager = listOf("cmdline-tools/latest/bin/sdkmanager", "tools/bin/sdkmanager").map { sdkDir.resolve(it) }.firstOrNull { it.exists() } ?: throw GradleException("sdkmanager not found")
            if (providers.exec { commandLine(sdkmanager.absolutePath, "cmake;$cmakeVersion"); isIgnoreExitValue = true }.result.get().exitValue != 0) throw GradleException("failed to install cmake")
            println("cmake $cmakeVersion installed")
        }
    }
}

getTargetAbis().forEach { abi ->
    tasks.register("buildOpencv_$abi") {
        group = "native"
        dependsOn("cloneOpencv", "checkCMake", "extractLibrariesFromApk")
        val installDir = opencvBuildDir(abi).resolve("install/sdk/native")
        outputs.dir(installDir)
        onlyIf { !skipOpenCV && !installDir.resolve("staticlibs/$abi/libopencv_quality.a").exists() }
        doLast {
            val buildDir = opencvBuildDir(abi).apply { mkdirs() }
            val flags = "-ffunction-sections -fdata-sections -fvisibility=hidden -Os"
            val cmakeArgs = buildList {
                add(getCMakeExecutable().absolutePath); add("-Wno-deprecated")
                add("-DCMAKE_TOOLCHAIN_FILE=${getNdkDir().resolve("build/cmake/android.toolchain.cmake").absolutePath}")
                add("-DANDROID_USE_LEGACY_TOOLCHAIN_FILE=OFF"); add("-DANDROID_ABI=$abi"); add("-DANDROID_PLATFORM=android-24"); add("-DANDROID_STL=c++_static")
                add("-DCMAKE_BUILD_TYPE=MinSizeRel"); add("-DCMAKE_INSTALL_PREFIX=${buildDir.absolutePath}/install"); add("-DBUILD_SHARED_LIBS=OFF")
                add("-DOPENCV_EXTRA_MODULES_PATH=${opencvDir.resolve("opencv_contrib/modules").absolutePath}")
                add("-DBUILD_LIST=core,imgproc,imgcodecs,ml,quality"); add("-DCPU_BASELINE=${cpuBaseline[abi] ?: ""}")
                cpuDispatch[abi]?.takeIf { it.isNotEmpty() }?.let { add("-DCPU_DISPATCH=$it") }
                listOf("TESTS", "PERF_TESTS", "ANDROID_EXAMPLES", "DOCS", "opencv_java", "opencv_python2", "opencv_python3", "opencv_apps", "EXAMPLES", "PACKAGE", "FAT_JAVA_LIB", "JASPER", "OPENEXR", "PROTOBUF", "JAVA", "OBJC", "ANDROID_PROJECTS").forEach { add("-DBUILD_$it=OFF") }
                add("-DBUILD_ZLIB=ON"); add("-DBUILD_PNG=ON"); add("-DBUILD_JPEG=ON")
                listOf("JPEG", "PNG").forEach { add("-DWITH_$it=ON") }
                listOf("TIFF", "WEBP", "OPENEXR", "JASPER", "OPENJPEG", "IMGCODEC_HDR", "IMGCODEC_SUNRASTER", "IMGCODEC_PXM", "IMGCODEC_PFM", "IPP", "EIGEN", "TBB", "OPENCL", "CUDA", "OPENGL", "VTK", "GTK", "QT", "GSTREAMER", "FFMPEG", "V4L", "1394", "ADE", "PROTOBUF", "QUIRC", "LAPACK", "OBSENSOR", "ANDROID_MEDIANDK", "ITT").forEach { add("-DWITH_$it=OFF") }
                add("-DOPENCV_ENABLE_NONFREE=OFF"); add("-DOPENCV_GENERATE_PKGCONFIG=OFF"); add("-DBUILD_INFO_SKIP_SYSTEM_VERSION=ON"); add("-DBUILD_INFO_SKIP_TIMESTAMP=ON"); add("-DENABLE_LTO=ON")
                add("-DCMAKE_CXX_FLAGS=$flags"); add("-DCMAKE_C_FLAGS=$flags"); add(opencvDir.resolve("opencv").absolutePath)
            }
            val env = mapOf("SOURCE_DATE_EPOCH" to "0", "TZ" to "UTC", "LANG" to "C.UTF-8")
            providers.exec { workingDir = buildDir; commandLine(cmakeArgs); env.forEach { (k, v) -> environment(k, v) } }.result.get()
            val makeResult = providers.exec { workingDir = buildDir; commandLine("make", "-j${Runtime.getRuntime().availableProcessors()}"); env.forEach { (k, v) -> environment(k, v) }; isIgnoreExitValue = true }.result.get()
            if (makeResult.exitValue != 0) makeProcess(buildDir, "make", "-j1")
            makeProcess(buildDir, "make", "install")
        }
    }
}

tasks.register("buildOpencv") { group = "native"; onlyIf { !skipOpenCV }; dependsOn(getTargetAbis().map { "buildOpencv_$it" }) }
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

tasks.whenTaskAdded {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) {
        dependsOn("checkCMake", "extractLibrariesFromApk", "buildOpencv")
        onlyIf { !skipOpenCV }
        doFirst { listOf("SOURCE_DATE_EPOCH" to "0", "TZ" to "UTC", "LANG" to "C.UTF-8").forEach { (k, v) -> System.setProperty(k, v) } }
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    dependsOn("extractLibrariesFromApk")
    if (!skipOpenCV) dependsOn("cleanJniLibs", "buildOpencv")
    if (!name.contains("debug", true)) { dependsOn("cleanApks"); if (signRelease) finalizedBy("moveApks") }
}
