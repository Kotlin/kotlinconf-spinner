[![JetBrains incubator project](http://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

<i>Small spinner-like game intended to demonstrate capabilities of Kotlin/Native software stack.</i>

<h2>Rules of the game</h2>
<ul>
    <li>Download and install mobile application for <a href="https://goo.gl/BsA73T">Android</a> or
        <a href="https://not.yet.here">iOS</a></li>
    <li>System will assign you to the random team, each team has its own color</li>
    <li>Spin Kotlin logo or shake your phone</li>
    <li>Each two full rotations (i.e. 720 degrees) will increment your team's score</li>
    <li>Team with the biggest score at the end of the conference wins</li>
    <li>Most active contributors from the winning team will get special prizes from us</li>
</ul>
<h2>Technical details</h2>
<ul>
    <li>Whole application's stack is implemented using <a href="https://github.com/JetBrains/kotlin-native">Kotlin/Native</a></li>
    <li><a href="https://github.com/JetBrains/kotlin-conf-demos/blob/master/kotlin-native/samples/fullstack/httpserver/HttpServer.kt">Server side</a>
        runs on Linux server and is implemented using:
        <ul>
          <li><a href="https://www.gnu.org/software/libmicrohttpd/">microHTTPD</a> HTTP server library</li>
          <li><a href="https://www.sqlite.org/">SQLite</a> for the database, storing score</li>
          <li><a href="http://www.digip.org/jansson/">Jansson</a> for client/server communications</li>
        </ul>
       </li>
    <li> <a href="https://github.com/JetBrains/kotlin-conf-demos/blob/master/kotlin-native/samples/fullstack/clients/android/src/main/kotlin/engine.kt">Client side</a>
        for Android is implemented in pure Kotlin/Native, as an Native Activity using:
        <ul>
         <li><a href="https://developer.android.com/guide/topics/graphics/opengl.html">GLES version 3</a> interop for 3D rendering</li>
         <li><a href="https://developer.android.com/ndk/reference/group___input.html">NDK input handling</a> for input processing</li>
         <li><a href="https://www.openal.org/">Open AL</a> interop for sound playback</li>
         <li><a href="https://developer.android.com/ndk/reference/group___sensor.html">Sensors native API</a></li>
         <li><a href="https://curl.haxx.se/libcurl/">libcurl</a> file transfer library
        </ul>
    <li><a href="https://github.com/JetBrains/kotlin-conf-demos/blob/master/kotlin-native/samples/fullstack/clients/ios">Client side</a>
       for iOS is implemented in pure Kotlin/Native using:
       <ul>
        <li><a href="https://developer.apple.com/documentation/opengles">GLES version 3 framework</a> for 3D rendering</li>
        <li>
          <a href="https://developer.apple.com/documentation/uikit">UIKit framework</a> for windows and views
        </li>
        <li>
          <a href="https://developer.apple.com/documentation/coremotion">CoreMotion framework</a> for sensors access
        </li>
        <li>
          <a href="https://developer.apple.com/library/content/documentation/Miscellaneous/Conceptual/iPhoneOSTechOverview/MediaLayer/MediaLayer.html#//apple_ref/doc/uid/TP40007898-CH9-SW13">OpenAL framework</a> for audio playback
        </li>
       </ul>
       </li>
    <li>Most graphical code, sound playback and and user input reaction is shared between Android and iOS</li>
    <li>Server interaction on Android is <a href="https://github.com/JetBrains/kotlin-conf-demos/blob/master/kotlin-native/samples/fullstack/clients/android/src/main/kotlin/engine.kt#L107">asynchronous</a>
        from the UI thread, using <a href="https://github.com/JetBrains/kotlin-native/tree/master/samples/workers">workers</a></li>
    <li>HTTP server works in multithreaded mode, state sharing between sessions performed via SQLite DB access</li>
    <li>Android app is split into separate <a href="https://github.com/JetBrains/kotlin-conf-demos/blob/master/kotlin-native/samples/fullstack/clients/android/src/loader/kotlin/loader.kt">loader</a>
     and application code, so that dynamic library (libopenal.so) included with application can be used on older Androids</li>
    <li><a href="https://github.com/JetBrains/kotlin-conf-demos/tree/master/kotlin-native/samples/fullstack/clients/webassembly">WebAssembly frontend</a>
               can fetch and render stats in the browser</li>
</ul>

