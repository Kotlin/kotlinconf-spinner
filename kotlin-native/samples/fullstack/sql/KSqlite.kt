package ksqlite

import sqlite3.*
import kotlinx.cinterop.*

typealias DbConnection = CPointer<sqlite3>?

class KSqliteError(message: String): Error(message)

private fun dbOpen(dbPath: String): DbConnection {
    val db = nativeHeap.alloc<CPointerVar<sqlite3>>()
    if (sqlite3_open(dbPath, db.ptr) != 0) {
        nativeHeap.free(db)
        throw KSqliteError("Cannot open db: ${sqlite3_errmsg(db.value)}")
    }
    return db.value
}

private fun fromCArray(ptr: CPointer<CPointerVar<ByteVar>>, count: Int): Array<String> =
        Array<String>(count, { index -> (ptr+index)!!.pointed.value!!.toKString() })

class KSqlite {
    var dbPath: String = ""
    var db: DbConnection = null

    constructor(dbPath: String) {
        db = dbOpen(dbPath)
    }

    constructor(db: COpaquePointer?) {
        this.db = db as DbConnection
    }

    val cpointer
        get() = db as COpaquePointer?

    fun execute(command: String, callback: ((Array<String>, Array<String>)-> Int)? = null) {
        memScoped {
            val error = this.alloc<CPointerVar<ByteVar>>()
            val callbackStable = if (callback != null) StableObjPtr.create(callback) else null
            try {
                if (sqlite3_exec(db, command, if (callback != null)
                    staticCFunction {
                        ptr, count, data, columns ->
                        val callbackFunction =
                                StableObjPtr.fromValue(ptr!!).get() as (Array<String>, Array<String>)-> Int
                        val columnsArray = fromCArray(columns!!, count)
                        val dataArray = fromCArray(data!!, count)
                        callbackFunction(columnsArray, dataArray)
                    } else null, callbackStable?.value, error.ptr) != 0)
                    throw KSqliteError("DB error: ${error.value!!.toKString()}")
            } finally {
                callbackStable?.dispose()
                sqlite3_free(error.value)
            }
        }
    }

    override fun toString(): String = "SQLite database in $dbPath"

    fun close() {
        if (db != null) {
            sqlite3_close(db)
            db = null
        }
    }
}

public inline fun withSqlite(path: String, function: (KSqlite) -> Unit) {
    val db = KSqlite(path)
    try {
        function(db)
    } finally {
        db.close()
    }
}

public inline fun withSqlite(db: KSqlite, function: (KSqlite) -> Unit) {
    try {
        function(db)
    } finally {
        db.close()
    }
}