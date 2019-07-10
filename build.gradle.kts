plugins {
    kotlin("multiplatform") version "1.3.41" apply false
    id("com.android.application") version "3.3.0" apply false
}

allprojects {
    repositories {
        jcenter()
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://dl.bintray.com/kotlin/kotlin-dev")
    }
}

val hostOs = System.getProperty("os.name")
val isMacos   by extra(hostOs == "Mac OS X")
val isLinux   by extra(hostOs == "Linux")
val isWindows by extra(hostOs.startsWith("Windows"))

extra["hostPresetName"] = when {
    isMacos -> "macosX64"
    isLinux -> "linuxX64"
    isWindows -> "mingwX64"
    else -> error("Unsupported host platform")
}

val libcurlPath by extra(file("external/curl/android"))
val janssonPath by extra(file("external/jansson/android"))
val openalPath  by extra(file("external/openal/android"))

val buildAll by tasks.creating {
    dependsOn(":httpserver:assemble")
    dependsOn(":clients:android:buildApk")
    dependsOn(":clients:ios:assemble")
    dependsOn(":clients:cli:assemble")
    dependsOn(":clients:webassembly:assemble")
    dependsOn(":utils:assemble")
}

val buildServer by tasks.creating {
    dependsOn(":httpserver:assemble")
}