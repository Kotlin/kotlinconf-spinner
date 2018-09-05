package kommon

import kotlinx.cinterop.*
import platform.Foundation.*

fun readResource(resourceName: String): ByteArray {
    val filePath = NSBundle.mainBundle.pathForResource(resourceName, ofType = null)

    val fileData = NSData.dataWithContentsOfFile(filePath!!)
            ?: throw Error("failed reading resource $resourceName")

    return fileData.bytes!!.readBytes(fileData.length.toInt())
}

