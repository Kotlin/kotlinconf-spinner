package kommon

import platform.posix.*
import kotlinx.cinterop.*

fun randomInit() = memScoped {
   val now = alloc<time_tVar>()
   time(now.ptr)
   srand(now.value.toInt())
} 
