import kotlinx.cinterop.*
import platform.android.*

fun main(args: Array<String>) {
    memScoped {
        val state = alloc<NativeActivityState>()
        getNativeActivityState(state.ptr)
        println("Light activated!")
        val engine = Engine(this, state)
        engine.mainLoop()
    }
}
