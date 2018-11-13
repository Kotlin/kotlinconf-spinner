[![JetBrains incubator project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) 

# Kotlin Spinner Game

Simple spinner-like game intended to demonstrate capabilities of Kotlin/Native software stack

## How to play

*   Download and install the mobile application for [Android](https://play.google.com/store/apps/details?id=com.jetbrains.konan_activity2) or [iOS](https://itunes.apple.com/us/app/kotlinconf-spinner/id1291282375?mt=8)
*   The system will automatically assign you to a random team. Each team has a unique colour
*   Spin the Kotlin logo using your fingers, or alternatively shake your phone
*   Each two full rotations (i.e. 720 degrees) will increment your team's score
*   The team with the highest score wins

## Technical details
The entire application is implemented using [Kotlin/Native](https://github.com/JetBrains/kotlin-native)

### Server-Side

[Server side](httpserver/src/main/kotlin/server/HttpServer.kt) runs on a linux server and is implemented using:

*   [microHTTPD](https://www.gnu.org/software/libmicrohttpd/) HTTP server library
*   [SQLite](https://www.sqlite.org/) for the database, storing score
*   [Jansson](http://www.digip.org/jansson/) for JSON serialization and client/server communication

### Client-Side 

#### Android 

[Client side](clients/android/src/main/kotlin/engine.kt) for Android is implemented in pure Kotlin/Native, as a Native Activity using:
*   [GLES version 3](https://developer.android.com/guide/topics/graphics/opengl.html) interop for 3D rendering
*   [NDK input handling](https://developer.android.com/ndk/reference/group___input.html) for input processing
*   [Open AL](https://www.openal.org/) interop for sound playback
*   [Sensors native API](https://developer.android.com/ndk/reference/group___sensor.html)
*   [libcurl](https://curl.haxx.se/libcurl/) file transfer library as HTTP client

#### iOS

[Client side](clients/ios/src/main/kotlin) for iOS is implemented in pure Kotlin/Native using:
*   [GLES version 3 framework](https://developer.apple.com/documentation/opengles) for 3D rendering
*   [UIKit framework](https://developer.apple.com/documentation/uikit) for windows and views
*   [CoreMotion framework](https://developer.apple.com/documentation/coremotion) for sensors access
*   [OpenAL framework](https://developer.apple.com/library/content/documentation/Miscellaneous/Conceptual/iPhoneOSTechOverview/MediaLayer/MediaLayer.html#//apple_ref/doc/uid/TP40007898-CH9-SW13) for audio playback

### Implementation details

*   Most graphical code, sound playback and user input reaction is [shared](clients/shared/src/main/kotlin) between Android and iOS
*   Server interaction on Android is [asynchronous](clients/android/src/main/kotlin/StatsFetcherImpl.kt#L66) from the UI thread, using [workers](https://github.com/JetBrains/kotlin-native/tree/master/samples/workers)
*   HTTP server works in multithreaded mode, state sharing between sessions performed via SQLite DB access
*   Android app is split into separate [loader](clients/android/src/loader/kotlin/loader.kt) and application code, so that dynamic library (libopenal.so) included with application can be used on older Androids
*   [WebAssembly frontend](clients/webassembly) can fetch and render stats in the browser

### Project Sources

Use JDK1.8, for Android compatibility, i.e.:
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_161.jdk/Contents/Home

To use microhttpd  (HTTP server) install it, i.e.:

    port install libmicrohttpd
    apt install libmicrohttpd-dev

To use jansson (JSON library) install it, i.e.:

    port install jansson
    apt install libjansson-dev

To use sqlite (embedded SQL server) install it:

    port install sqlite3
    apt install libsqlite3-dev

To use curl (HTTP client) install it:

    port install curl
    apt install libcurl3-nss
