import freetype2.*
import kotlinx.cinterop.*
import kommon.*
import kliopt.*
import platform.posix.mkdir

fun alignUp4(x: Int): Int = (x + 3) and 3.inv()

fun saveGlyphTo(glyph: FT_GlyphSlot, path: String) {
    val bpp = 32
    val bitmap = glyph.pointed.bitmap

    println("Producing bitmap ${bitmap.width} x ${bitmap.rows}")

    val headerSize = 14 /* sizeof(BITMAPFILEHEADER) */ + 124 /* sizeof(BITMAPV5HEADER) */
    val bmpHeaderData = ByteArray(headerSize)
    val bmpWidth = alignUp4(bitmap.width.toInt() * bpp / 8)
    val bmpDataSize = bitmap.rows.toInt() * bmpWidth

    bmpHeaderData.usePinned {
        pinned ->
        val bmpHeader = BMPHeader(pinned.addressOf(0).rawValue)
        bmpHeader.magic = 0x4d42
        bmpHeader.fileSize = bmpHeaderData.size.toInt() + bmpDataSize.toInt()
        bmpHeader.headerSize = headerSize - 14 /* sizeof(BITMAPFILEHEADER) */
        bmpHeader.dataOffset = bmpHeaderData.size
        bmpHeader.compressionMethod = 3
        bmpHeader.width = bitmap.width.convert()
        bmpHeader.height = bitmap.rows.convert()
        bmpHeader.colorPlanes = 1
        bmpHeader.bits = bpp.toShort()
        bmpHeader.redChannelMask   = 0x0000_00ffu
        bmpHeader.greenChannelMask = 0x0000_ff00u
        bmpHeader.blueChannelMask  = 0x00ff_0000u
        bmpHeader.alphaChannelMask = 0xff00_0000u
    }
    writeToFileData(path, bmpHeaderData)
    val bmpData = ByteArray(bmpDataSize)
    for (x in 0 until bitmap.width.toInt()) {
        for (y in 0 until bitmap.rows.toInt()) {
            val srcIndex = y * bitmap.width.toInt() + x
            val color = (bitmap.buffer + srcIndex)!!.pointed.value.toByte()
            val dstIndex = bmpWidth * (bitmap.rows.toInt() - 1 - y) + x * bpp / 8
            bmpData[dstIndex]     = color // Alpha channel.
            bmpData[dstIndex + 1] = color // Red.
            bmpData[dstIndex + 2] = color // Green.
            bmpData[dstIndex + 3] = color // Blue.

        }
    }
    writeToFileData(path, bmpData, true)
}

fun main(args: Array<String>) {

    var fontName = ""
    var fontSize = 70u
    var charsToRender = ""
    var directory = ""
    parseOptions(listOf(
            OptionDescriptor(OptionType.STRING, "f", "font", "Font to use", "/Library/Fonts/Andale Mono.ttf"),
            OptionDescriptor(OptionType.INT, "s", "size", "Size of the font", "72"),
            OptionDescriptor(OptionType.STRING, "c", "chars", "Characters to render", "0123456789"),
            OptionDescriptor(OptionType.STRING, "d", "directory", "Directory to use", "./glyphs")
    ), args).forEach {
        when (it.descriptor?.longName) {
            "font" -> fontName = it.stringValue
            "size" -> fontSize = it.intValue.toUInt()
            "chars" -> charsToRender = it.stringValue
            "directory" -> directory = it.stringValue
        }
    }

    mkdir(directory, 493 /* 0755 */ )

    memScoped {
        val ftLibrary = alloc<FT_LibraryVar>()
        if (FT_Init_FreeType(ftLibrary.ptr) != 0) {
            throw Error("Could not init freetype library")
        }
        val ftFace = alloc<FT_FaceVar>()
        if (FT_New_Face(ftLibrary.value, fontName, 0, ftFace.ptr) != 0) {
            throw Error("Could not open font $fontName")
        }
        FT_Set_Pixel_Sizes(ftFace.value, fontSize, fontSize)
        for (ch in charsToRender) {
            if (FT_Load_Char(ftFace.value, ch.toLong().convert(), FT_LOAD_RENDER.convert()) != 0) {
                throw Error("Could not load character $ch")
            }
            val glyph = ftFace.value!!.pointed.glyph
            saveGlyphTo(glyph!!, "$directory/$ch.bmp")
        }
    }
}
