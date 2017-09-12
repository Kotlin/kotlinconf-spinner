package kjson

import jansson.*
import kotlinx.cinterop.*

typealias Json = CPointer<json_t>?

class JsonError(message: String): Error(message)

class KJson {
    var json: Json = null

    constructor() {
        json = json_object()
    }

    constructor(text: String) {
        memScoped {
            val error = alloc<json_error_t>()
            json = json_loads(text, 0, error.ptr);
            if (json == null) {
                throw JsonError("json error on line ${error.line}: ${error.text}")
            }
        }
    }

    override fun toString(): String {
        if (json == null) return ""
        return json_dumps(json, JSON_ENCODE_ANY.toLong())?.let {
            val result = it.toKString()
            free(it)
            result
        } ?: ""
    }

    fun close() {
        if (json != null) {
            json_decref(json)
            json = null
        }
    }
}

public inline fun withJson(text: String, function: (KJson) -> Unit) {
    val json = KJson(text)
    try {
        function(json)
    } finally {
        json.close()
    }
}

public inline fun withJson(json: KJson, function: (KJson) -> Unit) {
    try {
        function(json)
    } finally {
        json.close()
    }
}