import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    val hostPresetName: String by rootProject.extra
    val libcurlPath: File by rootProject.extra

    // Declare targets.
    val host = targetFromPreset(presets[hostPresetName], "host") as KotlinNativeTarget
    val android32 = androidNativeArm32("android32")
    val android64 = androidNativeArm64("android64")

    // Configure sources and dependencies.
    val nativeMain by sourceSets.creating {
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib-common")
            api(project(":common"))
        }
    }

    configure(listOf(host, android32, android64)) {
        compilations["main"].apply {
            defaultSourceSet.dependsOn(nativeMain)

            // Configure cinterop.
            cinterops.create("libcurl") {
                if (target == android32 || target == android64) {
                    includeDirs("$libcurlPath/include")
                }
            }
        }
    }
}
