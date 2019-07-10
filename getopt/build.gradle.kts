plugins {
    kotlin("multiplatform")
}

kotlin {
    val hostPresetName: String by rootProject.extra
    targetFromPreset(presets[hostPresetName], "host")
}
