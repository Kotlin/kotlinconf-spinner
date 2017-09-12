package kurl

import kotlinx.cinterop.*
import libcurl.*

class KUrlError(message: String) : Error(message)

typealias EventHandler<T> = (T) -> Unit

class Event<T : Any> {
    private var handlers = emptyList<EventHandler<T>>()

    fun subscribe(handler: EventHandler<T>) {
        handlers += handler
    }

    fun unsubscribe(handler: EventHandler<T>) {
        handlers -= handler
    }

    operator fun plusAssign(handler: EventHandler<T>) = subscribe(handler)
    operator fun minusAssign(handler: EventHandler<T>) = unsubscribe(handler)

    operator fun invoke(value: T) {
        var exception: Throwable? = null
        for (handler in handlers) {
            try {
                handler(value)
            } catch (e: Throwable) {
                exception = e
            }
        }
        exception?.let { throw it }
    }
}

private fun CPointer<ByteVar>.toKString(length: Int): String {
    val bytes = ByteArray(length)
    nativeMemUtils.getByteArray(pointed, bytes, length)
    return kotlin.text.fromUtf8Array(bytes, 0, bytes.size)
}

class KUrl(val url: String, val cookies: String? = null)  {
    val stablePtr = StableObjPtr.create(this)

    val curl = curl_easy_init();

    init {
        curl_easy_setopt(curl, CURLOPT_URL, url)
        curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, staticCFunction {
            buffer: CPointer<ByteVar>?, size: size_t, nitems: size_t, userdata: COpaquePointer? -> size_t

            if (buffer == null) return@staticCFunction 0.toLong()
            val header = buffer.toKString((size * nitems).toInt()).trim()
            if (userdata != null) {
                val thiz = StableObjPtr.fromValue(userdata).get() as KUrl
                thiz.header(header)
            }
            return@staticCFunction size * nitems
        })
        curl_easy_setopt(curl, CURLOPT_HEADERDATA, stablePtr.value)

        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, staticCFunction {
            buffer: CPointer<ByteVar>?, size: size_t, nitems: size_t, userdata: COpaquePointer? -> size_t

            if (buffer == null) return@staticCFunction 0.toLong()
            val header = buffer.toKString((size * nitems).toInt())
            if (userdata != null) {
                val thiz = StableObjPtr.fromValue(userdata).get() as KUrl
                thiz.data(header)
            }
            return@staticCFunction size * nitems
        })
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, stablePtr.value)

        if (cookies != null) {
            curl_easy_setopt(curl, CURLOPT_COOKIEFILE, cookies)
            curl_easy_setopt(curl, CURLOPT_COOKIELIST, "RELOAD")
        }
    }

    val header = Event<String>()
    val data = Event<String>()

    fun fetch() {
        val res = curl_easy_perform(curl)
        if (res != CURLE_OK)
            throw KUrlError("curl_easy_perform() failed: ${curl_easy_strerror(res)?.toKString()?:""}")
    }

    fun close() {
        if (cookies != null)
            curl_easy_setopt(curl, CURLOPT_COOKIEJAR, cookies)
        curl_easy_cleanup(curl)
        stablePtr.dispose()
    }
}

public inline fun withKurl(kurl: KUrl, function: (KUrl) -> Unit) {
    try {
        function(kurl)
        kurl.fetch()
    } finally {
        kurl.close()
    }
}