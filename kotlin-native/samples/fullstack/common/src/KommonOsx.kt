package kommon

import platform.osx.*
import platform.posix.*
import kotlinx.cinterop.*

fun randomInit() = srandomdev()
