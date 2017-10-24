import kommon.*
import kotlinx.cinterop.*

fun extractChannelShift(mask: Int): Int {
    var shift = 0
    var x = mask
    while ((x and 1) == 0) {
        ++shift
        x = x shr 1
    }
    return shift
}

@Suppress("UNCHECKED_CAST")
fun convertBmp(fileName: String) {
    val data = readFileData(fileName) ?: throw Error("$fileName can not be read")
    data.usePinned { pinned ->
        val bmp = BMPHeader(pinned.addressOf(0).rawValue)
        println("BMP ${bmp.width}x${bmp.height}x${bmp.bits}")
        if (bmp.bits == 24.toShort()) {
            // Not entirely correct, just blindly byteswaps to get RGB.
            for (i in 0 until bmp.width * bmp.height) {
                val temp = bmp.data[3 * i + 2]
                bmp.data[3 * i + 2] = bmp.data[3 * i]
                bmp.data[3 * i] = temp
            }
        } else {
            if (bmp.version != 5) throw Error("Only BMP v5 is supported")
            val int32Data = bmp.data as CArrayPointer<IntVar>
            val numberOfInts = bmp.width * bmp.height
            val redShift = extractChannelShift(bmp.redChannelMask)
            val greenShift = extractChannelShift(bmp.greenChannelMask)
            val blueShift = extractChannelShift(bmp.blueChannelMask)
            val alphaShift = extractChannelShift(bmp.alphaChannelMask)
            bmp.redChannelMask = 0x0000_00ff
            bmp.greenChannelMask = 0x0000_ff00
            bmp.blueChannelMask = 0x00ff_0000
            bmp.alphaChannelMask = 0xff00_0000.toInt()
            for (i in 0 until numberOfInts) {
                val x = int32Data[i]
                int32Data[i] = (((x shr redShift) and 0xff) shl 0) or (((x shr greenShift) and 0xff) shl 8) or
                        (((x shr blueShift) and 0xff) shl 16) or (((x shr alphaShift) and 0xff) shl 24)
            }
        }
    }

    writeToFileData(fileName, data)
}

fun main(args: Array<String>) {
    for (file in args) {
        convertBmp(file)
    }
}