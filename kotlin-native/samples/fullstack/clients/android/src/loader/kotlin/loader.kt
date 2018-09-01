import platform.posix.*
import platform.android.*
import kotlinx.cinterop.*
import kotlin.text.*
import kotlin.system.exitProcess

// Hackaround showing how one could use dynamic libs on most Android versions.
// We explicitly preload "libopenal.so" and then load the actual activity.
val prefix by lazy {
    memScoped {
        val dlinfo = alloc<Dl_info>()
        if (dladdr(staticCFunction { -> }, dlinfo.ptr) != 0 && dlinfo.dli_fname != null) {
            val dli_fname = dlinfo.dli_fname!!.toKString()
            if (dli_fname.indexOf('/') == -1)
                "/data/data/com.jetbrains.konan_activity2/lib"
            else
                dlinfo.dli_fname!!.toKString().substringBeforeLast('/')
        } else {
            "."
        }
    }
}

fun loadKonanLibrary(name: String): COpaquePointer? {
    println("Loading $name...")
    val handle = dlopen("$prefix/lib$name.so", (RTLD_NOW or RTLD_GLOBAL).convert())
    if (handle == null) {
        println("cannot load $name from $prefix: ${dlerror()!!.toKString()}")
    }
    return handle
}

fun main(args: Array<String>) {
    println("Entering loader...")
    loadKonanLibrary("openal")
    var kotlin3d = loadKonanLibrary("kotlin3d")
    if (kotlin3d == null) {
        println("Cannot load main lib, trying light...")
        kotlin3d = loadKonanLibrary("kotlin3dlight")
        if (kotlin3d == null) {
            println("Cannot load even light version, die!")
            exitProcess(1)
        }
    }
    val entry = dlsym(kotlin3d, "Konan_main")?.reinterpret<
            CFunction<(COpaquePointer?, COpaquePointer?, size_t) -> Unit>>()
    if (entry != null) memScoped {
        val state = alloc<NativeActivityState>()
        getNativeActivityState(state.ptr)
        println("Calling entry point")
        entry(state.activity, state.savedState, state.savedStateSize)
    } else {
        println("main entry point not found...")
        exitProcess(2)
    }
}
