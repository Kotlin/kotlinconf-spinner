import kotlinx.cinterop.*
import platform.android.*
import kotlin.system.*
import platform.posix.*
import kommon.machineName
import kurl.*
import kjson.*
import konan.worker.*

fun main(args: Array<String>) {
    memScoped {
        val state = alloc<NativeActivityState>()
        getNativeActivityState(state.ptr)
        println("Light activated!")
        val engine = Engine(this, state)
        engine.mainLoop()
    }
}
