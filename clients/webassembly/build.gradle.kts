plugins {
    kotlin("multiplatform")
}

val staticDir = file("../../static")

kotlin {
    // Declare the target and the output binary.
    wasm32("wasm") {
        binaries.executable(listOf(RELEASE)) {
            baseName = "view"
            linkTask.destinationDir = staticDir
        }
    }

    // Depend on a prebuilt klib.
    sourceSets["wasmMain"].dependencies {
        implementation(rootProject.files("external/klib/dom.klib"))
    }
}

val copyIndex by tasks.creating(Copy::class.java) {
    destinationDir = staticDir
    from("index.html")
}

tasks.assemble {
    dependsOn(copyIndex)
}

tasks.clean {
    delete(staticDir)
}
