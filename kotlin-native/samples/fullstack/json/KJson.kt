package kjson

import jansson.*
import kotlinx.cinterop.*

typealias Json = CPointer<json_t>?

class JsonError(message: String): Error(message)

open class KJsonBase {
    var json: Json = null

    constructor() {}

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

private fun json_typeof(json: Json) = if (json == null) json_type.JSON_NULL else json.pointed.type

class KJsonObject : KJsonBase {
    constructor(text: String) {
        memScoped {
            val error = alloc<json_error_t>()
            json = json_loads(text, 0, error.ptr);
            if (json == null) {
                throw JsonError("json error on line ${error.line}: ${error.text}")
            }
        }
    }

    constructor(json: Json) {
        if (json_typeof(json) != json_type.JSON_OBJECT)
            throw JsonError("wrong type")
        this.json = json
    }

    constructor() {
        json = json_object()
    }

    fun getLong(key: String): Long {
        val value = json_object_get(json, key)
        if (value == null) throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_INTEGER)
            throw JsonError("wrong type")
        return json_integer_value(value)
    }

    fun getInt(key: String): Int = getLong(key).toInt()

    fun getString(key: String): String {
        val value = json_object_get(json, key)
        if (value == null) throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_STRING)
            throw JsonError("wrong type")
        return json_string_value(value)?.let {
            val result = it.toKString()
            free(it)
            result
        } ?: ""
    }

    fun getArray(key: String): KJsonArray {
        val value = json_object_get(json, key)
        if (value == null) throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_ARRAY)
            throw JsonError("wrong type")
        return KJsonArray(value)
    }

    fun getObject(key: String): KJsonObject {
        val value = json_object_get(json, key)
        if (value == null) throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_OBJECT)
            throw JsonError("wrong type")
        return KJsonObject(value)
    }
}

class KJsonArray : KJsonBase {
    constructor(text: String) {
        memScoped {
            val error = alloc<json_error_t>()
            json = json_loads(text, 0, error.ptr);
            if (json == null) {
                throw JsonError("json error on line ${error.line}: ${error.text}")
            }
        }
    }

    constructor(json: Json) {
        if (json_typeof(json) != json_type.JSON_ARRAY)
            throw JsonError("wrong type")
        this.json = json
    }

    constructor() {
        json = json_array()
    }

    fun getLong(index: Int): Long {
        val value = json_array_get(json, index.toLong())
        if (value == null) throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_INTEGER)
            throw JsonError("wrong type")
        return json_integer_value(value)
    }

    fun getInt(index: Int): Int = getLong(index).toInt()

    fun getString(index: Int): String {
        val value = json_array_get(json, index.toLong())
        if (value == null) throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_STRING)
            throw JsonError("wrong type")
        return json_string_value(value)?.let {
            val result = it.toKString()
            free(it)
            result
        } ?: ""
    }

    fun getArray(index: Int): KJsonArray {
        val value = json_array_get(json, index.toLong())
        if (value == null) throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_ARRAY)
            throw JsonError("wrong type")
        return KJsonArray(value)
    }

    fun getObject(index: Int): KJsonObject {
        val value = json_array_get(json, index.toLong())
        if (value == null) throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_OBJECT)
            throw JsonError("wrong type")
        return KJsonObject(value)
    }
}

public inline fun withJson(text: String, function: (KJsonObject) -> Unit) {
    val json = KJsonObject(text)
    try {
        function(json)
    } finally {
        json.close()
    }
}

public inline fun withJson(json: KJsonObject, function: (KJsonObject) -> Unit) {
    try {
        function(json)
    } finally {
        json.close()
    }
}