import jansson.*
import kjson.*
import konan.initRuntimeIfNeeded
import kotlin.system.exitProcess
import kotlin.text.toUtf8Array
import kotlinx.cinterop.*
import microhttpd.*
import ksqlite.*

typealias HttpConnection = CPointer<MHD_Connection>?

val MAX_COLORS = 5

data class Session(val color: Int, val name: String, val cookie: String)

// REST API goes here.

fun stats(db: KSqlite, color: Int, json: KJsonObject) {
    val colors = KJsonArray()
    db.execute("SELECT counter, color FROM colors") {
        _, data -> Int
        val record = KJsonObject()
        record.setInt("counter", data[0].toInt())
        record.setInt("color", data[1].toInt())
        colors.appendObject(record)
        0
    }
    json.setArray("colors", colors)
    json.setInt("color", color)
}

fun click(db: KSqlite, color: Int, json: KJsonObject) {
    db.execute("UPDATE colors SET counter = counter + 1 WHERE color=$color")
    stats(db, color, json)
}

// End of the REST API.

fun makeJson(url: String, db: KSqlite, session: Session): String {
    withJson(KJsonObject()) {
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

fun makeHtml(url: String, db: KSqlite, session: Session): String {
    return """
        <html><head>
            <title>Kotlin</title></head>
            <body>Hello ${session.name} from Kotlin/Native<br/>
            You used <b>$url</b><br/>
            Your color is <b>${session.color}</b><br/>
            </body></html>
"""
}

fun makeResponse(db: KSqlite, method: String, url: String, session: Session): Pair<String, String> {
    if (url.startsWith("/json"))
        return "application/json" to makeJson(url, db, session)

    return "text/html" to makeHtml(url, db, session)
}

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

fun makeSession(name: String, db: KSqlite): Session {
    println("Making session")
    val freshBakery = "${rand().toString(16)}${rand().toString(16)}${rand().toString(16)}"
    val color = rand() % MAX_COLORS + 1
    db.execute(
            "INSERT INTO sessions (cookie, color, name) VALUES ('$freshBakery', $color, '$name')")
    return Session(color, name, freshBakery)
}

fun initSession(http: HttpConnection, db: KSqlite): Session {
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
        db.execute("SELECT color,name FROM sessions WHERE cookie='$cookie'") {
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
    val dbMain = KSqlite("/tmp/clients.dblite")
    dbMain.execute(createDbCommand)
    dbMain.execute("SELECT count(*) FROM colors") {
        _, data -> Int
        var count = data[0].toInt()
        if (count < MAX_COLORS) {
           while (count++ < MAX_COLORS)
                dbMain.execute("INSERT INTO colors (counter) VALUES (0)")
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
                val db = KSqlite(cls)
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

        }, dbMain.cpointer,
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