package kurl

import kotlinx.cinterop.*
import libcurl.*

class KUrlError(message: String) : Error(message)

typealias HttpHandler = (String) -> Unit

private fun CPointer<ByteVar>.toKString(length: Int): String {
    val bytes = ByteArray(length)
    nativeMemUtils.getByteArray(pointed, bytes, length)
    return kotlin.text.fromUtf8Array(bytes, 0, bytes.size)
}

class KUrl(val url: String, val cookies: String? = null) {
    fun fetch(onData: HttpHandler, onHeader: HttpHandler?) {
        val curl = curl_easy_init();

        curl_easy_setopt(curl, CURLOPT_URL, url)

        if (cookies != null) {
            curl_easy_setopt(curl, CURLOPT_COOKIEFILE, cookies)
            curl_easy_setopt(curl, CURLOPT_COOKIELIST, "RELOAD")
        }
        val stables = mutableListOf<StableObjPtr>()
        val result = try {
            if (onHeader != null) {
                curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, staticCFunction { buffer: CPointer<ByteVar>?, size: size_t, nitems: size_t, userdata: COpaquePointer? ->

                    if (buffer == null) return@staticCFunction 0.toLong()
                    if (userdata != null) {
                        val handler = StableObjPtr.fromValue(userdata).get() as HttpHandler
                        handler(buffer.toKString((size * nitems).toInt()).trim())
                    }
                    return@staticCFunction size * nitems
                })
                val onHeaderStable = StableObjPtr.create(onHeader)
                stables += onHeaderStable
                curl_easy_setopt(curl, CURLOPT_HEADERDATA, onHeaderStable.value)
            }

            curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, staticCFunction { buffer: CPointer<ByteVar>?, size: size_t, nitems: size_t, userdata: COpaquePointer? ->

                if (buffer == null) return@staticCFunction 0.toLong()
                val header = buffer.toKString((size * nitems).toInt())
                if (userdata != null) {
                    val handler = StableObjPtr.fromValue(userdata).get() as HttpHandler
                    handler(header)
                }
                return@staticCFunction size * nitems
            })
            val onDataStable = StableObjPtr.create(onData)
            stables += onDataStable
            curl_easy_setopt(curl, CURLOPT_WRITEDATA, onDataStable.value)

            curl_easy_perform(curl)
        } finally {
            stables.forEach {
                it.dispose()
            }
            if (cookies != null)
                curl_easy_setopt(curl, CURLOPT_COOKIEJAR, cookies)
            curl_easy_cleanup(curl)
        }

        if (result != CURLE_OK)
            throw KUrlError("curl_easy_perform() failed: ${curl_easy_strerror(result)?.toKString() ?: ""}")
    }

    // So that we can use DSL syntax.
    fun fetch(onData: HttpHandler) = fetch(onData, null)
}