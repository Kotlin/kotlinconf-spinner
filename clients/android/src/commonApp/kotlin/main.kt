import kotlinx.cinterop.*
import platform.android.*

internal expect fun printAppVariant()

fun main(args: Array<String>) {
    memScoped {
        val state = alloc<NativeActivityState>()
        getNativeActivityState(state.ptr)
        printAppVariant()
        val engine = Engine(this, state)
        engine.mainLoop()
    }
}
