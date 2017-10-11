package kommon

import platform.posix.*
import kotlinx.cinterop.*

fun randomInit() = srandomdev()
