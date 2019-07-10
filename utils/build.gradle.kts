import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE

plugins {
    kotlin("multiplatform")
}

kotlin {
    val hostPresetName: String by rootProject.extra
    val isMacos: Boolean by rootProject.extra
    val isLinux: Boolean by rootProject.extra

    // Declare and configure a target.
    val host = targetFromPreset(presets[hostPresetName], "host") as KotlinNativeTarget
    host.apply {
        // We create a new compilation for each utility program.
        val bmpConvertor by compilations.creating
        val fontGenerator by compilations.creating {
            // Configure the interop.
            cinterops.create("freetype") {
                when {
                    isMacos -> includeDirs("/opt/local/include")
                    isLinux -> includeDirs("/usr/include")
                }
            }
        }

        // Declare output executables.
        binaries.executable("FontGenerator", listOf(RELEASE)) {
            compilation = fontGenerator
        }

        binaries.executable("BmpConverter", listOf(RELEASE)) {
            compilation = bmpConvertor
        }
    }

    // Configure sources and dependencies.
    // Source sets are created for each compilation automatically.
    sourceSets["hostBmpConvertor"].apply {
        kotlin.srcDir("src/BmpConvertor")
        dependencies {
            implementation(project(":common"))
        }
    }

    sourceSets["hostFontGenerator"].apply {
        kotlin.srcDir("src/FontGenerator")
        dependencies {
            implementation(project(":common"))
            implementation(project(":getopt"))
        }
    }
}

// Copy the output executables into the project directory.
val copyOutput by tasks.creating(Copy::class.java) {
    val host = kotlin.targets["host"] as KotlinNativeTarget
    val bmpConvertor = host.binaries.getExecutable("BmpConverter", RELEASE)
    val fontGenerator = host.binaries.getExecutable("FontGenerator", RELEASE)

    dependsOn(bmpConvertor.linkTask)
    dependsOn(fontGenerator.linkTask)
    tasks["assemble"].dependsOn(this)

    destinationDir = projectDir
    from(bmpConvertor.outputFile)
    from(fontGenerator.outputFile)
}
