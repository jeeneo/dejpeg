import java.io.File
import java.util.Properties

run {
    val localPropsFile = rootDir.resolve("local.properties")
    val localProps = Properties()
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { stream -> localProps.load(stream) }
    }
    val cmakeBinPath = System.getenv("CMAKE_BIN")
        ?: ProcessBuilder("sh", "-c", "command -v cmake")
            .redirectErrorStream(true)
            .start()
            .let { proc ->
                val out = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                out.ifEmpty { null }
            }
    if (cmakeBinPath != null) {
        val cmakeHome = File(cmakeBinPath).canonicalFile.parentFile.parentFile.absolutePath
        if (localProps.getProperty("cmake.dir") != cmakeHome) {
            localProps.setProperty("cmake.dir", cmakeHome)
            localPropsFile.outputStream().use { stream -> localProps.store(stream, null) }
            println("Added cmake.dir=$cmakeHome into local.properties")
        }
        else {
            // println("cmake.dir=$cmakeHome already exists in local.properties")
        }
    }
    else {
        println("CMake binary not found in PATH. Please install CMake or set the CMAKE_BIN environment variable.")
    }
}

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DeJPEG"
include(":app")
