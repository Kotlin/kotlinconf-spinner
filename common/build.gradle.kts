plugins {
    kotlin("multiplatform")
}

kotlin {
    val hostPresetName: String by rootProject.extra
    val isMacos: Boolean by rootProject.extra
    val isLinux: Boolean by rootProject.extra

    // Declare targets.
    targetFromPreset(presets[hostPresetName], "host")
    androidNativeArm32("android32")
    androidNativeArm64("android64")
    iosArm64("iosDevice")
    iosX64("iosSim")

    // Configure sources.
    // We cannot place the shared code into the commonMain source set
    // because this code uses platform libraries and breaks metadata compilation.
    // So we create a separate source set for them and don't use commonMain at all.
    // This approach is used in other modules too.
    val nativeMain by sourceSets.creating {
        dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib-common")
        }
    }

    val androidMain by sourceSets.creating
    val iosMain by sourceSets.creating
    val osxMain by sourceSets.creating
    val linuxMain by sourceSets.creating

    configure(listOf(androidMain, iosMain, osxMain, linuxMain)) {
        dependsOn(nativeMain)
    }

    sourceSets["android32Main"].dependsOn(androidMain)
    sourceSets["android64Main"].dependsOn(androidMain)
    sourceSets["iosSimMain"].dependsOn(iosMain)
    sourceSets["iosDeviceMain"].dependsOn(iosMain)

    when {
        isMacos -> sourceSets["hostMain"].dependsOn(osxMain)
        isLinux -> sourceSets["hostMain"].dependsOn(linuxMain)
    }
}
