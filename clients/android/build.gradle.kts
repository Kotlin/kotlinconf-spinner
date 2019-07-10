import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE

plugins {
    kotlin("multiplatform")
    id("com.android.application")
}

repositories {
    google()
}

val libcurlPath: File by rootProject.extra
val janssonPath: File by rootProject.extra
val openalPath: File by rootProject.extra

// Configure K/N executables.
kotlin {
    // Declare targets.
    val android32 = androidNativeArm32("android32")
    val android64 = androidNativeArm64("android64")

    // Configure sources.
    // The commonLoader source set includes all code for the loader library.
    val commonLoader by sourceSets.creating {
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
        }
    }

    // The commonApp source set includes all common code
    // for both regular and light application variants.
    val commonApp by sourceSets.creating {
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
            implementation(project(":clients:shared"))
            implementation(project(":common"))
            implementation(project(":json"))
            implementation(project(":kurl"))
        }
    }

    // The regularApp source set includes code specific
    // for the regular application variant (with audio)
    // for both arm32 and arm64 targets.
    val regularApp by sourceSets.creating {
        dependsOn(commonApp)
        dependencies {
            implementation(project(":clients:audio"))
        }
    }

    // The lightApp source set includes code specific
    // for the light application variant (without audio)
    // for both arm32 and arm64 targets.
    val lightApp by sourceSets.creating {
        dependsOn(commonApp)
    }

    // Configure targets.
    configure(listOf(android32, android64)) {
        // Use the default main compilation for the regular app.
        compilations["main"].defaultSourceSet.dependsOn(regularApp)

        // Declare compilations for the loader and the light app.
        val loader by compilations.creating {
            defaultSourceSet.dependsOn(commonLoader)
        }

        val light by compilations.creating {
            defaultSourceSet.dependsOn(lightApp)
        }

        // Declare final binaries for all libraries.
        binaries {
            executable("loader", listOf(RELEASE)) {
                compilation = loader
                baseName = "libloader"
            }

            executable("regular", listOf(RELEASE)) {
                // The main compilation is used by default.
                baseName = "libkotlin3d"
                when(target) {
                    android32 -> linkerOpts(
                        "-L$libcurlPath/lib/armeabi-v7a", "-lcurl",
                        "$janssonPath/arm32/lib/libjansson.a",
                        "-L$openalPath/lib/armeabi-v7a", "-lopenal", "-lz"
                    )
                    android64 -> linkerOpts(
                        "-L$libcurlPath/lib/arm64-v8a", "-lcurl",
                        "$janssonPath/arm64/lib/libjansson.a",
                        "-L$openalPath/lib/arm64-v8a", "-lopenal", "-lz"
                    )
                }
            }

            executable("light", listOf(RELEASE)) {
                compilation = light
                baseName = "libkotlin3dlight"
                when(target) {
                    android32 -> linkerOpts(
                        "-L$libcurlPath/lib/armeabi-v7a", "-lcurl",
                        "$janssonPath/arm32/lib/libjansson.a",
                        "-lz"
                    )
                    android64 -> linkerOpts(
                        "-L$libcurlPath/lib/arm64-v8a", "-lcurl",
                        "$janssonPath/arm64/lib/libjansson.a",
                        "-lz"
                    )
                }
            }
        }
    }
}

// Configure copying K/N binaries into output directories.
val outDir = file("out")
val libsDir = outDir.resolve("libs")
val platforms = mapOf(
    kotlin.targets["android32"] as KotlinNativeTarget to "armeabi-v7a",
    kotlin.targets["android64"] as KotlinNativeTarget to "arm64-v8a"
)

val copyLibs by tasks.creating(Copy::class.java) {
    destinationDir = libsDir
    platforms.forEach { (target, subdir) ->
        val loader = target.binaries.getExecutable("loader", RELEASE)
        val regular = target.binaries.getExecutable("regular", RELEASE)
        val light = target.binaries.getExecutable("light", RELEASE)

        dependsOn(loader.linkTask)
        dependsOn(regular.linkTask)
        dependsOn(light.linkTask)

        into(subdir) {
            from(loader.outputFile)
            from(regular.outputFile)
            from(light.outputFile)
            from(file("$openalPath/lib/$subdir/libopenal.so"))
        }
    }
}


val deleteOut by tasks.creating(Delete::class.java) {
    delete(outDir)
}

tasks["clean"].dependsOn(deleteOut)
tasks.matching { it.name == "preBuild" }.all {
    dependsOn(copyLibs)
}

// Configure the Android application.
android {
    compileSdkVersion(26)

    defaultConfig {
        applicationId = "com.jetbrains.konan_activity2"
        minSdkVersion(19)
        targetSdkVersion(26)

        ndk {
            abiFilters("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs.create("release") {
        if (project.hasProperty("RELEASE_STORE_FILE")) {
            storeFile = file(property("RELEASE_STORE_FILE")!!)
            storePassword = property("RELEASE_STORE_PASSWORD") as String
            keyAlias = property("RELEASE_KEY_ALIAS") as String
            keyPassword = property("RELEASE_KEY_PASSWORD") as String
        }
    }

    buildTypes["debug"].apply {
        isDebuggable = true
        ndk {
            isDebuggable = true
        }
    }

    buildTypes["release"].apply {
        // Add RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS
        // and RELEASE_KEY_PASSWORD to ~/.gradle/gradle.properties
        // to sign release build. Keystore could be created from
        // Android Studio.
        if (project.hasProperty("RELEASE_STORE_FILE")) {
            signingConfig = signingConfigs["release"]
        }
    }

    sourceSets["main"].jniLibs.srcDir(libsDir)
    lintOptions.isAbortOnError = false
}

val buildApk by tasks.creating(Copy::class.java) {
    dependsOn("assembleDebug", "assembleRelease")
    destinationDir = outDir
    from("$buildDir/outputs/apk")
}
