import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    // Declare targets.
    val iosDevice = iosArm64("iosDevice")
    val iosSim = iosX64("iosSim")
    val android64 = androidNativeArm64("android64")
    val android32 = androidNativeArm32("android32")

    // Configure sources and dependencies.
    val nativeMain by sourceSets.creating {
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib-common")
            api(project(":clients:shared"))
        }
    }

    targets.withType(KotlinNativeTarget::class.java) {
        // All platform source sets depend on nativeMain.
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
    }

    // Configure targets.
    configure(listOf(android32, android64)) {
        compilations["main"].apply {
            // Declare OpenAL interop for Android.
            cinterops.create("openal") {
                val openalPath: File by rootProject.extra
                includeDirs("$openalPath/include")
            }
        }
    }
}
