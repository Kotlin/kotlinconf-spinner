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
            api(project(":common"))
        }
    }

    val iosMain by sourceSets.creating {
        dependsOn(nativeMain)
    }

    val androidMain by sourceSets.creating {
        dependsOn(nativeMain)
    }

    configure(listOf(iosDevice, iosSim)) {
        // Use common iOS source set.
        compilations["main"].defaultSourceSet.dependsOn(iosMain)
    }

    configure(listOf(android32, android64)) {
        // Use common Android source set.
        compilations["main"].defaultSourceSet.dependsOn(androidMain)
    }
}
