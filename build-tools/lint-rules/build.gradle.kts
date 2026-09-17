import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)
}

kotlin {
    jvmToolchain(17)
}
