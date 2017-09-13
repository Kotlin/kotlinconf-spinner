package kommon

import common.*
import kotlinx.cinterop.*

fun allocMemory(size: Int) = common.calloc(1, size.signExtend())
fun freeMemory(ptr: COpaquePointer) = common.free(ptr)

fun random() = common.random()
