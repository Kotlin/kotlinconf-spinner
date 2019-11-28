import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    // Declare a target.
    val hostPresetName: String by rootProject.extra
    val host = targetFromPreset(presets[hostPresetName], "host") as KotlinNativeTarget

    host.apply {
        // Configure an interop.
        compilations["main"].cinterops.create("microhttpd")
        // Declare an output executable.
        binaries.executable(listOf(DEBUG)) {
            baseName = "HttpServer"
        }
    }

    // Declare dependencies.
    sourceSets["hostMain"].dependencies {
        implementation(project(":common"))
        implementation(project(":json"))
        implementation(project(":sql"))
        implementation(project(":getopt"))
    }
}
