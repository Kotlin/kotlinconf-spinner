package kommon

import common.*
import kotlinx.cinterop.*

fun allocMemory(size: Int) = common.calloc(1, size.signExtend())
fun freeMemory(ptr: COpaquePointer) = common.free(ptr)

fun random() = common.random()

fun readFileData(path: String): ByteArray? {
    return memScoped {
        val info = alloc<stat>()
        if (stat(path, info.ptr) != 0) return null
        // We're not planning to serve files > 4G, right?
        val size = info.st_size.toInt()
        val result = ByteArray(size)
        val file = fopen(path, "r")
        if (file == null) return null
        var position = 0
        while (position < size) {
            val toRead = minOf(size - position, 4096)
            val read = fread(result.refTo(position), 1, toRead.signExtend(), file).toInt()
            if (read <= 0) break
            position += read
        }
        fclose(file)
        result
    }
}

fun sockaddrAsString(sockaddr: CPointer<sockaddr>?, socklen: socklen_t) =
    memScoped {
        val iptext = allocArray<ByteVar>(64)
        val porttext = allocArray<ByteVar>(64)
        if (getnameinfo(sockaddr, socklen, iptext, 64, porttext, 64,
                        NI_NUMERICHOST or NI_NUMERICSERV) == 0)
            "${iptext.toKString()}:${porttext.toKString()}"
        else
            "unknown"
    }

fun machineName() =
        memScoped {
            val u = alloc<utsname>()
            if (uname(u.ptr) == 0) {
                "${u.sysname.toKString()} ${u.machine.toKString()}"
            } else {
                "unknown"
            }
        }

fun usleep(microseconds: Int) = common.usleep(microseconds)