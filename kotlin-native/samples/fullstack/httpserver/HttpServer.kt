import jansson.*
import kjson.*
import konan.initRuntimeIfNeeded
import kotlin.system.exitProcess
import kotlin.text.toUtf8Array
import kotlinx.cinterop.*
import microhttpd.*
import sqlite3.*

typealias DbConnection = CPointerVar<sqlite3>
typealias HttpConnection = CPointer<MHD_Connection>?

val MAX_COLORS = 5

data class Session(val color: Int, val name: String, val cookie: String)

// REST API goes here.

fun stats(db: DbConnection, color: Int, json: KJson) {
    val colors = json_array()
    dbExecute(db, "SELECT counter, color FROM colors") {
        _, data -> Int
        val record = json_object()
        json_object_set_new(record, "counter", json_integer(data[0].toLong()))
        json_object_set_new(record, "color", json_integer(data[1].toLong()))
        json_array_append_new(colors, record)
        0
    }
    json_object_set_new(json.json, "colors", colors)
    json_object_set_new(json.json, "color", json_integer(color.toLong()))
}

fun click(db: DbConnection, color: Int, json: KJson) {
    dbExecute(db, "UPDATE colors SET counter = counter + 1 WHERE color=$color")
    stats(db, color, json)
}

// End of the REST API.

fun makeJson(url: String, db: DbConnection, session: Session): String {
    withJson(KJson()) {
        json -> Unit

        json_object_set_new(json.json, "url", json_string(url))
        when {
            url.startsWith("/json/click") -> click(db, session.color, json)
            url.startsWith("/json/stats") -> stats(db, session.color, json)
        }
        return json.toString()
    }
    return ""
}

fun makeHtml(url: String, db: DbConnection, session: Session): String {
    return """
        <html><head>
            <title>Kotlin</title></head>
            <body>Hello ${session.name} from Kotlin/Native<br/>
            You used <b>$url</b><br/>
            Your color is <b>${session.color}</b><br/>
            </body></html>
"""
}

fun makeResponse(db: DbConnection, method: String, url: String, session: Session): Pair<String, String> {
    if (url.startsWith("/json"))
        return "application/json" to makeJson(url, db, session)

    return "text/html" to makeHtml(url, db, session)
}

val dbName = "/tmp/clients.dblite"
// `rowid` column is always there in sqlite, so no need to create explicit
// primary key.
val createDbCommand = """
    CREATE TABLE IF NOT EXISTS sessions(
        name VARCHAR(255),
        color INT NOT NULL,
        cookie VARCHAR(64) NOT NULL
    );
    CREATE TABLE IF NOT EXISTS colors(
        color INTEGER PRIMARY KEY AUTOINCREMENT,
        counter
    );

"""

fun dbOpen(): DbConnection {
    val db = nativeHeap.alloc<DbConnection>()
    if (sqlite3_open(dbName, db.ptr) != 0) {
        throw Error("Cannot open db: ${sqlite3_errmsg(db.value)}")
    }
    return db
}

fun fromCArray(ptr: CPointer<CPointerVar<ByteVar>>, count: Int): Array<String> = Array<String>(count, {
    index -> (ptr+index)!!.pointed.value!!.toKString()
})

fun dbExecute(db: DbConnection,
              command: String, callback: ((Array<String>, Array<String>)-> Int)? = null) {
    memScoped {
        val error = this.alloc<CPointerVar<ByteVar>>()
        val callbackStable = if (callback != null) StableObjPtr.create(callback) else null
        try {
            if (sqlite3_exec(db.value, command, if (callback != null)
                        staticCFunction {
                            ptr, count, data, columns -> Int
                            val callbackFunction =
                                StableObjPtr.fromValue(ptr!!).get() as (Array<String>, Array<String>)-> Int
                            val columnsArray = fromCArray(columns!!, count)
                            val dataArray = fromCArray(data!!, count)
                            callbackFunction(columnsArray, dataArray)
                        } else null, callbackStable?.value, error.ptr) != 0)
                throw Error("DB error: ${error.value!!.toKString()}")
        } finally {
            callbackStable?.dispose()
            sqlite3_free(error.value)
        }
    }
}

fun makeSession(name: String, db: DbConnection): Session {
    println("Making session")
    val freshBakery = "${rand().toString(16)}${rand().toString(16)}${rand().toString(16)}"
    val color = rand() % MAX_COLORS + 1
    dbExecute(db,
            "INSERT INTO sessions (cookie, color, name) VALUES ('$freshBakery', $color, '$name')")
    return Session(color, name, freshBakery)
}

fun initSession(http: HttpConnection, db: DbConnection): Session {
    var name = MHD_lookup_connection_value(http, MHD_GET_ARGUMENT_KIND, "name") ?. toKString() ?: "Unknown"
    val cookieC = MHD_lookup_connection_value(http, MHD_COOKIE_KIND, "cookie")
    return if (cookieC == null) {
        // No cookie set yet, authenticate?
        println("no cookie found, creating one for $name!")
        makeSession(name, db)
    } else {
        val cookie = cookieC.toKString()
        var color = -1

        val suppliedName = name
        dbExecute(db, "SELECT color,name FROM sessions WHERE cookie='$cookie'") {
            _, data -> Int

            color = data[0].toInt()
            name = data[1]
            0
        }
        println("name is $name, supplied $suppliedName")

        if (color == -1) {
            // There's cookie, but we do not remember it.
            println("We cannot remember the cookie, how come? Cookie is $cookie.")
            makeSession(name, db)
        } else
            Session(color, name, cookie)
    }
}

fun main(args: Array<String>) {
    if (args.size != 1 || args[0].toIntOrNull() == null) {
        println("HttpServer <port>")
        exitProcess(1)
    }
    val port = args[0].toInt().toShort()
    val dbMain = dbOpen()
    dbExecute(dbMain, createDbCommand)
    dbExecute(dbMain, "SELECT count(*) FROM colors") {
        _, data -> Int
        var count = data[0].toInt()
        if (count < MAX_COLORS) {
           while (count++ < MAX_COLORS)
                dbExecute(dbMain, "INSERT INTO colors (counter) VALUES (0)")
        }
        0
    }

    val daemon = MHD_start_daemon(MHD_USE_AUTO or MHD_USE_INTERNAL_POLLING_THREAD or MHD_USE_ERROR_LOG,
        port, null, null, staticCFunction {
            cls, connection, urlC, methodC, _, _, _, _ -> Int
            // This handler could (and will) be invoked in another per-connection
            // thread, so reinit runtime.
            konan.initRuntimeIfNeeded()
            // Do this, as otherwise exception may be not caught.
            // TODO: is it correct?
            try {
                val db = cls!!.reinterpret<CPointerVar<sqlite3>>().pointed
                val session = initSession(connection, db)
                val url = urlC?.toKString() ?: ""
                val method = methodC?.toKString() ?: ""
                println("Connection to $url method $method")
                if (method != "GET") return@staticCFunction MHD_NO
                val (contentType, responseText) = makeResponse(db, method, url, session)
                return@staticCFunction memScoped {
                    val responseArray = toUtf8Array(responseText, 0, responseText.length)
                    val response = MHD_create_response_from_buffer(
                            responseArray.size.toLong(),
                            responseArray.toCValues().getPointer(this),
                            MHD_ResponseMemoryMode.MHD_RESPMEM_MUST_COPY)
                    val expires = "Tue, 8 Sep 2018 21:43:04 GMT"
                    MHD_add_response_header(response, "Content-Type", contentType)
                    MHD_add_response_header(response, "Set-Cookie", "cookie=${session.cookie}; Expires=$expires")
                    val result = MHD_queue_response(connection, MHD_HTTP_OK, response)
                    MHD_destroy_response(response);
                    result
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                return@staticCFunction MHD_NO
            }

        }, dbMain.ptr,
        MHD_OPTION_CONNECTION_TIMEOUT, 120,
        MHD_OPTION_STRICT_FOR_CLIENT, 1,
        MHD_OPTION_END)
    if (daemon == null) {
        println("Cannot start daemon")
        exitProcess(2)
    }
    println("Server started, connect to http://localhost:$port, press Enter to exit...")
    readLine()
    MHD_stop_daemon(daemon)
}