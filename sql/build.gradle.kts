import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    val hostPresetName: String by rootProject.extra
    val host = targetFromPreset(presets[hostPresetName], "host") as KotlinNativeTarget

    host.apply {
        compilations["main"].cinterops.create("sqlite3")
    }
}
