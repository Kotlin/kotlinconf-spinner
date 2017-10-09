package kommon

import kotlinx.cinterop.*
import android.*

fun readResource(resourceName: String): ByteArray {
    memScoped {
        val state = alloc<NativeActivityState>()
        getNativeActivityState(state.ptr)
        val nativeActivity = state.activity!!.pointed
        val asset = AAssetManager_open(nativeActivity.assetManager, resourceName, AASSET_MODE_BUFFER)
        if (asset == null)
            error("Error opening asset $resourceName")
        try {
            val length = AAsset_getLength(asset)
            val result = ByteArray(length.toInt())
            if (AAsset_read(asset, result.refTo(0), length) != result.size)
                error("Error reading asset $resourceName")
            return result
        } finally {
            AAsset_close(asset)
        }
    }
}

