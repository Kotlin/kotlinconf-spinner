import kjson.*
import ksqlite.*
import kliopt.*
import microhttpd.*
import kommon.*
import common.sockaddr
import common.socklen_t

import konan.initRuntimeIfNeeded
import kotlin.system.exitProcess
import kotlin.text.toUtf8Array
import kotlinx.cinterop.*

typealias HttpConnection = CPointer<MHD_Connection>?

val MAX_COLORS = 5
val serverRoot = "./"

data class Session(val color: Int, val name: String, val cookie: String)

// REST API goes here.

fun stats(db: KSqlite, session: Session, json: KJsonObject) {
    val colors = KJsonArray()
    db.execute("SELECT counter, color FROM colors") {
        _, data ->
        val record = KJsonObject()
        record.setInt("counter", data[0].toInt())
        record.setInt("color", data[1].toInt())
        colors.appendObject(record)
        0
    }
    json.setArray("colors", colors)
    json.setInt("color", session.color)
    db.execute("SELECT counter FROM sessions WHERE cookie='${session.cookie}'") { _, data ->
        json.setInt("contribution", data[0].toInt())
        0
    }
}

fun click(db: KSqlite, session: Session, json: KJsonObject) {
    db.execute("UPDATE colors SET counter = counter + 1 WHERE color=${session.color}")
    db.execute("UPDATE sessions SET counter = counter + 1 WHERE cookie='${session.cookie}'")
    stats(db, session, json)
}

// End of the REST API.

fun makeJson(url: String, db: KSqlite, session: Session): String {
    withJson(KJsonObject()) {
        it.setString("url", url)
        when {
            url.startsWith("/json/click") -> click(db, session, it)
            url.startsWith("/json/stats") -> stats(db, session, it)
        }
        return it.toString()
    }
    return ""
}

fun makeHtml(url: String, session: Session): String {
    return """
        <html><head>
            <title>Kotlin</title></head>
            <body>Hello ${session.name} from Kotlin/Native<br/>
            You used <b>$url</b><br/>
            Your color is <b>${session.color}</b><br/>
            </body></html>
"""
}

val contentTypes = mapOf<String, String>(
        "js" to "application/javascript",
        "html" to "text/html",
        "wasm" to "application/javascript"
)

fun makeStaticContent(url: String): Pair<String, ByteArray> {
    val file = url.split('/').last()
    var fullName = "$serverRoot/static/$file"
    val extension = file.split('.').last()

    val content = readFileData(fullName) ?: ByteArray(0)

    return (contentTypes.get(extension) ?: "text/html") to content
}

fun String.asData() : ByteArray = toUtf8Array(this, 0, this.length)

fun makeResponse(db: KSqlite, url: String, session: Session): Pair<String, ByteArray> {
    if (url.startsWith("/json"))
        return "application/json" to makeJson(url, db, session).asData()

    if (url.startsWith("/static"))
        return makeStaticContent(url)

    return "text/html" to makeHtml(url, session).asData()
}

// `rowid` column is always there in sqlite, so no need to create explicit
// primary key.
val createDbCommand = """
    CREATE TABLE IF NOT EXISTS sessions(
        name VARCHAR(255),
        color INT NOT NULL,
        cookie VARCHAR(64) NOT NULL PRIMARY KEY,
        counter INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS colors(
        color INTEGER PRIMARY KEY AUTOINCREMENT,
        counter INTEGER
    );
    CREATE TABLE IF NOT EXISTS connections(
        ip VARCHAR(64),
        timestamp DATE
    );
    CREATE TABLE IF NOT EXISTS auth(
        secret VARCHAR(64)
    );
"""

fun rnd() = kommon.random()

fun makeSession(name: String, db: KSqlite): Session {
    println("Making session")
    val freshBakery = "${rnd().toString(16)}${rnd().toString(16)}${rnd().toString(16)}"
    val color = (rnd() % MAX_COLORS + 1).toInt()
    db.execute(
            "INSERT INTO sessions (cookie, color, name, counter) VALUES ('$freshBakery', $color, '$name', 0)")
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
            _, data ->

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

fun logConnection(db: KSqlite, sockaddr: CPointer<sockaddr>?, socklen: socklen_t) {
    val ip = kommon.sockaddrAsString(sockaddr, socklen)
    println("connection from $ip")
    db.execute("INSERT INTO connections (ip, timestamp) VALUES ('$ip', DateTime('now'))")
}

fun main(args: Array<String>) {
    val cliOptions = listOf(
            OptionDescriptor(OptionType.INT, "p", "port", "Port to use", "8080"),
            OptionDescriptor(OptionType.BOOLEAN, "h", "help", "Usage info"),
            OptionDescriptor(OptionType.BOOLEAN, "d", "daemon", "Run as daemon"),
            OptionDescriptor(OptionType.STRING, "s", "secret", "Secret for admin access")
    )
    var port = 8080
    var isDaemon = false
    var secret: String? = null
    parseOptions(cliOptions, args).forEach {
        when (it.descriptor?.longName) {
            "port" -> port = it.intValue
            "daemon" -> isDaemon = true
            "help" -> println(makeUsage(cliOptions))
            "secret" -> secret = it.stringValue
        }
    }

    kommon.randomInit()

    val dbMain = KSqlite("$serverRoot/clients.dblite")
    dbMain.execute(createDbCommand)
    dbMain.execute("SELECT count(*) FROM colors") {
        _, data ->
        var count = data[0].toInt()
        if (count < MAX_COLORS) {
           while (count++ < MAX_COLORS)
                dbMain.execute("INSERT INTO colors (counter) VALUES (0)")
        }
        0
    }

    if (secret != null) {
        dbMain.execute("REPLACE INTO auth (secret) VALUES ('$secret')")
    }

    // Was MHD_USE_INTERNAL_POLLING_THREAD or MHD_USE_AUTO or MHD_USE_ERROR_LOG
    val options = MHD_USE_POLL_INTERNALLY
    val daemon = MHD_start_daemon(options, port.toShort(), staticCFunction {
            cls, addr, addrlen ->
            konan.initRuntimeIfNeeded()
            val db = KSqlite(cls)
            logConnection(db, addr, addrlen)
            MHD_YES
    }, dbMain.cpointer, staticCFunction {
            cls, connection, urlC, methodC, _, _, _, _ ->
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
                val machine =  MHD_lookup_connection_value(connection, MHD_GET_ARGUMENT_KIND, "machine") ?. toKString() ?: "?"
                val userAgent = MHD_lookup_connection_value(connection, MHD_HEADER_KIND, "User-Agent") ?. toKString() ?: "?"
                println("Connection to $url method $method from $machine agent $userAgent")
                if (method != "GET") return@staticCFunction MHD_NO
                val (contentType, responseArray) = makeResponse(db, url, session)
                return@staticCFunction memScoped {
                    val response = MHD_create_response_from_buffer(
                            responseArray.size.signExtend(),
                            responseArray.refTo(0),
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
        // MHD_OPTION_STRICT_FOR_CLIENT, 1,
    MHD_OPTION_END)
    if (daemon == null) {
        println("Cannot start daemon")
        exitProcess(2)
    }
    if (isDaemon) {
        println("Server started at http://localhost:$port going to background...")
        while (true) {
            usleep(1000000)
        }
    } else {
        println("Server started, connect to http://localhost:$port, press Enter to exit...")
        readLine()
    }
    MHD_stop_daemon(daemon)
    dbMain.close()
}
