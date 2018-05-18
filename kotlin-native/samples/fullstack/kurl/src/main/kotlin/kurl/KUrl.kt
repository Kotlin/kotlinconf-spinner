package kurl

import kotlinx.cinterop.*
import libcurl.*
import platform.posix.size_t

class KUrlError(message: String) : Error(message)

typealias HttpHandler = (String) -> Unit

private fun CPointer<ByteVar>.toKString(length: Int) = this.readBytes(length).stringFromUtf8()

class KUrl(val cookies: String? = null) {
    var curl = curl_easy_init()

    fun escape(string: String) =
      curl_easy_escape(curl, string, 0) ?. let {
        val result = it.toKString()
        curl_free(it)
        result
      } ?: ""

    fun fetch(url: String, options: Map<String, String>?, onData: HttpHandler, onHeader: HttpHandler?) {
        val args =
                if (options != null)
                    "?" + options.map { (key, value) -> "$key=${escape(value)}"}.joinToString("&")
                else
                    ""
        curl_easy_setopt(curl, CURLOPT_URL, url + args)
        if (cookies != null) {
            curl_easy_setopt(curl, CURLOPT_COOKIEFILE, cookies)
            curl_easy_setopt(curl, CURLOPT_COOKIELIST, "RELOAD")
        }
        val stables = mutableListOf<StableRef<Any>>()
        val result = try {
            if (onHeader != null) {
                curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, staticCFunction {
                    buffer: CPointer<ByteVar>?, size: size_t, nitems: size_t, userdata: COpaquePointer? ->

                    if (buffer == null) return@staticCFunction 0.toLong()
                    val handler = userdata!!.asStableRef<HttpHandler>().get()
                    handler(buffer.toKString((size * nitems).toInt()).trim())
                    return@staticCFunction (size * nitems).toLong()
                })
                val onHeaderStable = StableRef.create(onHeader)
                stables += onHeaderStable
                curl_easy_setopt(curl, CURLOPT_HEADERDATA, onHeaderStable.asCPointer())
            }

            curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, staticCFunction {
                buffer: CPointer<ByteVar>?, size: size_t, nitems: size_t, userdata: COpaquePointer? ->

                if (buffer == null) return@staticCFunction 0.toLong()
                val header = buffer.toKString((size * nitems).toInt())
                val handler = userdata!!.asStableRef<HttpHandler>().get()
                handler(header)
                return@staticCFunction (size * nitems).toLong()
            })
            val onDataStable = StableRef.create(onData)
            stables += onDataStable
            curl_easy_setopt(curl, CURLOPT_WRITEDATA, onDataStable.asCPointer())

            curl_easy_perform(curl)
        } finally {
            stables.forEach {
                it.dispose()
            }
        }

        if (result != CURLE_OK)
            throw KUrlError("curl_easy_perform() failed with code $result: ${curl_easy_strerror(result)?.toKString() ?: ""}")
    }

    fun close() {
         if (curl == null) return
         if (cookies != null)
                curl_easy_setopt(curl, CURLOPT_COOKIEJAR, cookies)
         curl_easy_cleanup(curl)
         curl = null
    }

    // So that we can use DSL syntax.
    fun fetch(url: String, options: Map<String, String>? = null, onData: HttpHandler) =
            fetch(url, options, onData,null)
}

inline fun <T> withUrl(url: KUrl, function: (KUrl) -> T): T =
    try {
        function(url)
    } finally {
        url.close()
    }
