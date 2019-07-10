package kjson

import jansson.*
import kotlinx.cinterop.*
import kommon.freeMemory

typealias Json = CPointer<json_t>?

class JsonError(message: String): Error(message)

open class KJsonBase() {
    var json: Json = null

    override fun toString(): String {
        if (json == null) return ""
        return json_dumps(json, JSON_ENCODE_ANY.convert())?.let {
            val result = it.toKString()
            freeMemory(it)
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
            json = json_loads(text, 0, error.ptr)
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
        val value = json_object_get(json, key) ?: throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_INTEGER)
            throw JsonError("wrong type")
        return json_integer_value(value)
    }

    fun getInt(key: String): Int = getLong(key).toInt()

    fun getString(key: String): String {
        val value = json_object_get(json, key) ?: throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_STRING)
            throw JsonError("wrong type")
        return json_string_value(value)?.let {
            val result = it.toKString()
            freeMemory(it)
            result
        } ?: ""
    }

    fun getArray(key: String): KJsonArray {
        val value = json_object_get(json, key) ?: throw JsonError("no value")
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

    fun setLong(key: String, value: Long) {
        json_object_set_new(json, key, json_integer(value))
    }

    fun setInt(key: String, value: Int) {
        json_object_set_new(json, key, json_integer(value.toLong()))
    }

    fun setString(key: String, value: String) {
        json_object_set_new(json, key, json_string(value))
    }

    fun setArray(key: String, value: KJsonArray) {
        json_object_set_new(json, key, value.json)
    }

    fun setObject(key: String, value: KJsonObject) {
        json_object_set_new(json, key, value.json)
    }
}

class KJsonArray : KJsonBase {
    constructor(text: String) {
        memScoped {
            val error = alloc<json_error_t>()
            json = json_loads(text, 0, error.ptr)
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
        val value = json_array_get(json, index.convert()) ?: throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_INTEGER)
            throw JsonError("wrong type")
        return json_integer_value(value)
    }

    fun getInt(index: Int): Int = getLong(index).toInt()

    fun getString(index: Int): String {
        val value = json_array_get(json, index.convert()) ?: throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_STRING)
            throw JsonError("wrong type")
        return json_string_value(value)?.let {
            val result = it.toKString()
            freeMemory(it)
            result
        } ?: ""
    }

    fun getArray(index: Int): KJsonArray {
        val value = json_array_get(json, index.convert()) ?: throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_ARRAY)
            throw JsonError("wrong type")
        return KJsonArray(value)
    }

    fun getObject(index: Int): KJsonObject {
        val value = json_array_get(json, index.convert()) ?: throw JsonError("no value")
        if (json_typeof(value) != json_type.JSON_OBJECT)
            throw JsonError("wrong type")
        return KJsonObject(value)
    }

    fun setLong(index: Int, value: Long) =
            json_array_set(json, index.convert(), json_integer(value))

    fun setInt(index: Int, value: Int) =
            setLong(index, value.toLong())

    fun setString(index: Int, value: String) =
            json_array_set(json, index.convert(), json_string(value))

    fun setArray(index: Int, value: KJsonArray) =
            json_array_set(json, index.convert(), value.json)

    fun setObject(index: Int, value: KJsonObject) =
            json_array_set(json, index.convert(), value.json)

    fun appendLong(value: Long) = json_array_append_new(json, json_integer(value))

    fun appendInt(value: Int) = appendLong(value.toLong())

    fun appendString(value: String) = json_array_append_new(json, json_string(value))

    fun appendObject(value: KJsonObject) =
        json_array_append_new(json, value.json)

    fun appendArray(value: KJsonArray) =
        json_array_append_new(json, value.json)

    val size: Int get() = json_array_size(json).toInt()
}

inline fun withJson(text: String, function: (KJsonObject) -> Unit) {
    val json = KJsonObject(text)
    try {
        function(json)
    } finally {
        json.close()
    }
}

inline fun withJson(json: KJsonObject, function: (KJsonObject) -> Unit) {
    try {
        function(json)
    } finally {
        json.close()
    }
}
