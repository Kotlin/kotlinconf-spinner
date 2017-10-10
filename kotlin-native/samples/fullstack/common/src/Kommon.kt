package kommon

import platform.posix.*
import kotlinx.cinterop.*

fun allocMemory(size: Int) = calloc(1, size.signExtend())
fun freeMemory(ptr: COpaquePointer) = free(ptr)

fun random() = platform.posix.random()

fun readFileData(path: String): ByteArray? {
    return memScoped {
        val info = alloc<stat>()
        if (stat(path, info.ptr) != 0) return null
        // We're not planning to serve files > 4G, right?
        val size = info.st_size.toInt()
        val result = ByteArray(size)
        val file = fopen(path, "rb")
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

fun writeToFileData(path: String, data: ByteArray, append: Boolean = false) {
    return memScoped {
        val file = fopen(path, if (append) "ab" else "wb")
        if (file == null) throw Error("Cannot write to $file")
        var position = 0
        while (position < data.size) {
            val toWrite = minOf(data.size - position, 4096)
            val written = fwrite(data.refTo(position), 1, toWrite.signExtend(), file).toInt()
            if (written <= 0) break
            position += written
        }
        fclose(file)
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
            val u = alloc<platform.posix.utsname>()
            if (uname(u.ptr) == 0) {
                "${u.sysname.toKString()} ${u.machine.toKString()}"
            } else {
                "unknown"
            }
        }

fun usleep(microseconds: Int) = platform.posix.usleep(microseconds)

class BMPHeader(val rawPtr: NativePtr) {
    inline fun <reified T : CPointed> memberAt(offset: Long): T {
        return interpretPointed<T>(this.rawPtr + offset)
    }

    var magic: Short
        get() = memberAt<ShortVar>(0).value
        set(value) { memberAt<ShortVar>(0).value = value }

    var fileSize: Int
        get() = memberAt<IntVar>(2).value
        set(value) { memberAt<IntVar>(2).value = value }

    var dataOffset: Int
        get() = memberAt<IntVar>(10).value
        set(value) { memberAt<IntVar>(10).value = value }

    var headerSize: Int
        get() = memberAt<IntVar>(14).value
        set(value) { memberAt<IntVar>(14).value = value }

    var width: Int
        get() = memberAt<IntVar>(18).value
        set(value) { memberAt<IntVar>(18).value = value }

    var height: Int
        get() = memberAt<IntVar>(22).value
        set(value) { memberAt<IntVar>(22).value = value }

    var colorPlanes: Short
        get() = memberAt<ShortVar>(26).value
        set(value) { memberAt<ShortVar>(26).value = value }

    var bits: Short
        get() = memberAt<ShortVar>(28).value
        set(value) { memberAt<ShortVar>(28).value = value }

    var compressionMethod: Int
        get() = memberAt<IntVar>(30).value
        set(value) { memberAt<IntVar>(30).value = value }

    var imageSize: Int
        get() = memberAt<IntVar>(34).value.toInt()
        set(value) { memberAt<IntVar>(34).value = value }

    var redChannelMask: Int
        get() = memberAt<IntVar>(54).value.toInt()
        set(value) { memberAt<IntVar>(54).value = value }

    var greenChannelMask: Int
        get() = memberAt<IntVar>(58).value.toInt()
        set(value) { memberAt<IntVar>(58).value = value }

    var blueChannelMask: Int
        get() = memberAt<IntVar>(62).value.toInt()
        set(value) { memberAt<IntVar>(62).value = value }

    var alphaChannelMask: Int
        get() = memberAt<IntVar>(66).value.toInt()
        set(value) { memberAt<IntVar>(66).value = value }

    val data
        get() = interpretCPointer<ByteVar>(rawPtr + 14 + headerSize.toLong()) as CArrayPointer<ByteVar>

    val version
        get() = when (headerSize) {
            40 -> 3
            124 -> 5
            else -> TODO()
        }
}