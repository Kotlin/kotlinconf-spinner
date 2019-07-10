pluginManagement {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        maven("https://dl.bintray.com/kotlin/kotlin-dev")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
    }

    resolutionStrategy {
        eachPlugin {
            // Allow applying android plugin using the Plugin DSL in kts.
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

rootProject.name = "kotlin-native-demo"

// Allow using a K/N distribution built locally from sources.
gradle.startParameter.projectProperties["konan.build"]?.let { konanBuildPath ->
    gradle.allprojects {
        extensions.extraProperties.set("org.jetbrains.kotlin.native.home", "$konanBuildPath/dist")
    }
}

// Disable Android if SDK isn't configured.
val disableAndroid = gradle.startParameter.projectProperties["disable.android"]
if (disableAndroid == null || disableAndroid.toLowerCase() != "true") {
    if (System.getenv("ANDROID_HOME") != null || rootProject.projectDir.resolve("local.properties").isFile()) {
        include(":clients:android")
    } else {
        logger.warn("WARN. Android SDK may not be configured, excluding Android project...")
    }
}

include(":clients:cli")
include(":clients:ios")
include(":clients:webassembly")
include(":clients:shared")
include(":clients:audio")
include(":common")
include(":getopt")
include(":httpserver")
include(":json")
include(":kurl")
include(":sql")
include(":utils")

enableFeaturePreview("GRADLE_METADATA")
