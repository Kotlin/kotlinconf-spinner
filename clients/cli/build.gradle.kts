import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE

plugins {
    kotlin("multiplatform")
}

kotlin {
    // Declare a target.
    val hostPresetName: String by rootProject.extra
    val host = targetFromPreset(presets[hostPresetName], "host") as KotlinNativeTarget
    host.binaries.executable(listOf(RELEASE)) {
        baseName = "CliClient"
    }

    // Declare dependencies.
    sourceSets["hostMain"].dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
        implementation(project(":common"))
        implementation(project(":json"))
        implementation(project(":sql"))
        implementation(project(":kurl"))
        implementation(project(":getopt"))
    }
}
