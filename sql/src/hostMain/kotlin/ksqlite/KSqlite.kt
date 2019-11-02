package ksqlite

import sqlite3.*
import kotlinx.cinterop.*

typealias DbConnection = CPointer<sqlite3>?

class KSqliteError(message: String): Error(message)

private fun fromCArray(ptr: CPointer<CPointerVar<ByteVar>>, count: Int) =
        Array(count, { index -> (ptr+index)!!.pointed.value!!.toKString() })

class KSqlite {
    var dbPath: String = ""
    var db: DbConnection = null

    constructor(dbPath: String) {
        memScoped {
          val dbPtr = alloc<CPointerVar<sqlite3>>()
          if (sqlite3_open(dbPath, dbPtr.ptr) != 0) {
             throw KSqliteError("Cannot open db: ${sqlite3_errmsg(dbPtr.value)}")
          }
          db = dbPtr.value
        }
    }

    constructor(db: COpaquePointer?) {
        this.db = db?.reinterpret()
    }

    val cpointer
        get() = db as COpaquePointer?

    fun execute(command: String, callback: ((Array<String>, Array<String>)-> Int)? = null) {
        memScoped {
            val error = this.alloc<CPointerVar<ByteVar>>()
            val callbackStable = if (callback != null) StableRef.create(callback) else null
            try {
                if (sqlite3_exec(db, command, if (callback != null)
                    staticCFunction {
                        ptr, count, data, columns ->
                        val callbackFunction =
                                ptr!!.asStableRef<(Array<String>, Array<String>) -> Int>().get()
                        val columnsArray = fromCArray(columns!!, count)
                        val dataArray = fromCArray(data!!, count)
                        callbackFunction(columnsArray, dataArray)
                    } else null, callbackStable?.asCPointer(), error.ptr) != 0)
                    throw KSqliteError("DB error: ${error.value!!.toKString()}")
            } finally {
                callbackStable?.dispose()
                sqlite3_free(error.value)
            }
        }
    }

    // TODO: use sql3_prepare instead!
    fun escape(input: String): String = input.replace("'", "''")

    override fun toString(): String = "SQLite database in $dbPath"

    fun close() {
        if (db != null) {
            sqlite3_close(db)
            db = null
        }
    }
}

inline fun withSqlite(path: String, function: (KSqlite) -> Unit) {
    val db = KSqlite(path)
    try {
        function(db)
    } finally {
        db.close()
    }
}

inline fun withSqlite(db: KSqlite, function: (KSqlite) -> Unit) {
    try {
        function(db)
    } finally {
        db.close()
    }
}
