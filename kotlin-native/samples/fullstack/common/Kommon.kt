package kommon

import common.*
import kotlinx.cinterop.*

fun allocMemory(size: Int) = common.calloc(1, size.signExtend())
fun freeMemory(ptr: COpaquePointer) = common.free(ptr)

fun random() = common.random()

fun readFileData(path: String): ByteArray? {
    return memScoped {
        val info = alloc<statStruct>()
        if (stat(path, info.ptr) != 0) return null
        // We're not planning to serve files > 4G, right?
        val size = info.st_size.toInt()
        val result = ByteArray(size)
        val file = fopen(path, "r")
        if (file == null) return null
        val BUFFER_SIZE = 4096
        val buffer = allocArray<ByteVar>(BUFFER_SIZE)
        val bufferArray = ByteArray(BUFFER_SIZE)
        var read = 0
        var position = 0
        // TODO: double copy, rethink!
        while (read < size) {
            read = fread(buffer, 1.signExtend(), BUFFER_SIZE.signExtend(), file).toInt()
            if (read <= 0) break
            nativeMemUtils.getByteArray(buffer.pointed, bufferArray, read)
            bufferArray.copyRangeTo(result, 0, read, position)
            position += read
        }
        result
    }
}