import kjson.*
import ksqlite.*
import kliopt.*
import microhttpd.*
import kommon.*
import platform.posix.*

import konan.initRuntimeIfNeeded
import kotlin.system.exitProcess
import kotlin.text.toUtf8Array
import kotlinx.cinterop.*
import kotlin.system.exitProcess

typealias HttpConnection = CPointer<MHD_Connection>?

val MAX_COLORS = 5
val serverRoot = "./"

data class Session(val color: Int, val name: String, val cookie: String, val password: String)

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
    db.execute("SELECT startTime, status FROM games WHERE current = 'true'") {
        _, data ->
        json.setString("startTime", data[0])
        json.setInt("status", data[1].toInt())
        0
    }
    json.setArray("colors", colors)
    json.setInt("color", session.color)
    db.execute("SELECT counter, winner FROM sessions WHERE cookie='${db.escape(session.cookie)}'") { _, data ->
        json.setInt("contribution", data[0].toInt())
        json.setInt("winner", data[1].toInt())
        0
    }
}

fun click(db: KSqlite, session: Session, json: KJsonObject) {
    var running = false
    db.execute("SELECT status FROM games WHERE current = 'true'") {
        _, data ->
        // Check if game is currently in progress.
        running = (data[0].toInt() == 1)
        0
    }
    if (running) {
        db.execute("UPDATE colors SET counter = counter + 1 WHERE color=${session.color}")
        db.execute("UPDATE sessions SET counter = counter + 1 WHERE cookie='${db.escape(session.cookie)}'")
    }
    stats(db, session, json)
}

// Admin API.
fun start(db: KSqlite, session: Session, json: KJsonObject) {
    if (!session.isAdmin(db)) {
        error(json, session,"Unauthorized")
        return
    }
    db.execute("UPDATE games SET current = 'false' WHERE current = 'true'")
    db.execute("INSERT INTO games (current, status, startTime) VALUES ('true', 1, DateTime('now'))")
    db.execute("UPDATE colors SET counter = 0")
    db.execute("UPDATE sessions SET counter = 0, winner = 0")
    stats(db, session, json)
}

fun resume(db: KSqlite, session: Session, json: KJsonObject) {
    if (!session.isAdmin(db)) {
        error(json, session,"Unauthorized")
        return
    }
    db.execute("UPDATE games SET status = 1, winner=NULL WHERE current = 'true'")
    db.execute("UPDATE sessions SET winner = 0")
    stats(db, session, json)
}

fun setWinner(db: KSqlite, endGame: Boolean) {
    var winner = -1
    db.execute("SELECT color FROM colors ORDER BY counter DESC LIMIT 1") {
        _, data ->
        winner = data[0].toInt()
        0
    }

    println("Winner team is $winner!!!!!")
    db.execute("UPDATE games SET status = 0, winner=$winner WHERE current = 'true'")
    if (endGame) {
        db.execute("UPDATE games SET endTime = DateTime('now') WHERE current = 'true'")
    }

    // Now update top 10 contributors.
    val winners = mutableListOf<String>()
    db.execute("SELECT cookie FROM sessions WHERE color=$winner ORDER BY counter DESC LIMIT 10") {
        _, data ->
        winners += data[0]
        0
    }
    println("Winners are ${winners.joinToString(", ")}")
    winners.forEach {
        db.execute("UPDATE sessions SET winner = 1 WHERE cookie='$it'")
    }
}

fun pause(db: KSqlite, session: Session, json: KJsonObject) {
    if (!session.isAdmin(db)) {
        error(json, session,"Unauthorized")
        return
    }
    setWinner(db, false)
    stats(db, session, json)
}

fun stop(db: KSqlite, session: Session, json: KJsonObject) {
    if (!session.isAdmin(db)) {
        error(json, session,"Unauthorized")
        return
    }
    setWinner(db, true)
    stats(db, session, json)
}

// End of the REST API.

fun Session.isAdmin(db: KSqlite): Boolean {
    var admin = false
    db.execute("SELECT secret FROM auth WHERE user='root'") {
        _, data ->
        admin = data[0] == this.password
        0
    }
    return admin
}

fun error(json: KJsonObject, session: Session, message: String) {
    json.setString("error", message)
    json.setInt("color", session.color)
}

fun makeJson(url: String, db: KSqlite, session: Session): String {
    withJson(KJsonObject()) {
        when {
            url.startsWith("/json/click") -> click(db, session, it)
            url.startsWith("/json/stats") -> stats(db, session, it)
            url.startsWith("/json/start") -> start(db, session, it)
            url.startsWith("/json/pause") -> pause(db, session, it)
            url.startsWith("/json/resume") -> resume(db, session, it)
            url.startsWith("/json/stop") -> stop(db, session, it)
            else -> error(it, session,"Unknown command")
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
        winner INT,
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
        user VARCHAR(64) NOT NULL PRIMARY KEY,
        secret VARCHAR(64)
    );
    CREATE TABLE IF NOT EXISTS games(
        current BOOLEAN NOT NULL,
        status INTEGER NOT NULL,
        winner INTEGER,
        startTime DATE,
        endTime DATE
    );
"""

fun rnd() = kommon.random()

fun makeSession(name: String, password: String, db: KSqlite): Session {
    println("Making session")
    val freshBakery = "${rnd().toString(16)}${rnd().toString(16)}${rnd().toString(16)}"
    val color = (rnd() % MAX_COLORS + 1).toInt()
    db.execute(
            "INSERT INTO sessions (cookie, color, name, counter, winner) VALUES ('$freshBakery', $color, '${db.escape(name)}', 0, 0)")
    return Session(color, name, freshBakery, password)
}

fun initSession(http: HttpConnection, db: KSqlite): Session {
    var name = MHD_lookup_connection_value(http, MHD_GET_ARGUMENT_KIND, "name") ?. toKString() ?: "Unknown"
    var password = MHD_lookup_connection_value(http, MHD_GET_ARGUMENT_KIND, "password") ?. toKString() ?: ""
    val cookieC = MHD_lookup_connection_value(http, MHD_COOKIE_KIND, "cookie")
    return if (cookieC == null) {
        // No cookie set yet, authenticate?
        println("no cookie found, creating one for $name!")
        makeSession(name, password, db)
    } else {
        val cookie = cookieC.toKString()
        var color = -1

        val suppliedName = name
        db.execute("SELECT color,name FROM sessions WHERE cookie='${db.escape(cookie)}'") {
            _, data ->

            color = data[0].toInt()
            name = data[1]
            0
        }
        println("name is $name, supplied $suppliedName")

        if (color == -1) {
            // There's cookie, but we do not remember it.
            println("We cannot remember the cookie, how come? Cookie is $cookie.")
            makeSession(name, password, db)
        } else
            Session(color, name, cookie, password)
    }
}

fun logConnection(db: KSqlite, sockaddr: CPointer<sockaddr>?,
                  socklen: platform.posix.socklen_t) {
    val ip = kommon.sockaddrAsString(sockaddr, socklen)
    println("connection from $ip")
    db.execute("INSERT INTO connections (ip, timestamp) VALUES ('$ip', DateTime('now'))")
}

fun showUsage(message: String) {
    println(message)
    exitProcess(1)
}

fun additionalOptions(https: Boolean): Array<Any> {
    if (!https) return emptyArray<Any>()

    // Self-signed demo certificate, produced with
    // openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout my.key -out my.crt
    val certPem = """-----BEGIN CERTIFICATE-----
MIID4zCCAsugAwIBAgIJAKHE86g9UIUqMA0GCSqGSIb3DQEBCwUAMIGHMQswCQYD
VQQGEwJDWjEOMAwGA1UECAwFUHJhaGExDjAMBgNVBAcMBVByYWhhMRIwEAYDVQQK
DAlKZXRCcmFpbnMxFzAVBgNVBAMMDk5pa29sYXkgSWdvdHRpMSswKQYJKoZIhvcN
AQkBFhxuaWtvbGF5Lmlnb3R0aUBqZXRicmFpbnMuY29tMB4XDTE3MTAwMjE0NDcx
N1oXDTE4MTAwMjE0NDcxN1owgYcxCzAJBgNVBAYTAkNaMQ4wDAYDVQQIDAVQcmFo
YTEOMAwGA1UEBwwFUHJhaGExEjAQBgNVBAoMCUpldEJyYWluczEXMBUGA1UEAwwO
Tmlrb2xheSBJZ290dGkxKzApBgkqhkiG9w0BCQEWHG5pa29sYXkuaWdvdHRpQGpl
dGJyYWlucy5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCpsm1W
SEDW/+8U9/BBRpTCLlDA7Wc8o5ut9vfJx1usCkGHUYI2NPUlQI8AAfjcfagbi3ug
UPEpuEK7vR5y4IlN26ur1t+wzvYyhIDN1W9y0gUO+MRWIkFi+kPlPPLQfmN05VmA
s9P9NlHWM3rJ6ywC255mqWqUmcsUn8zVWpW8FJ5rVd8PNS9jdoV3jKiTgcP/s9g3
pjtMN2uPabK3p5gANzowsJbMPPzXxgh8VF/0mC3MycPy86h+qof+FiftTKJe7zXH
x1m7q0pQfz1U+REwzsu9+6NfrRD4GeKNBxEZWVghsAOZ8PUuyl7coPfBFCZx9Hdx
R7MzbGVV8LWzYXk9AgMBAAGjUDBOMB0GA1UdDgQWBBSdJpTXPAopb97CuKDzTpkt
1brKITAfBgNVHSMEGDAWgBSdJpTXPAopb97CuKDzTpkt1brKITAMBgNVHRMEBTAD
AQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCWGa9rPCdX/w5Hnz8Uc4sxkWvptxeq+n/L
AlptoYMq1TRFDeKdpf09JVnGq+fUblTBxRRLBKYRJFXisU8uOfsUWgJvhWyY+CFC
xHuYllkIZTLmBnh6P42OVf2oWxg78grghG4U6rltj6m/uatLOnYd54WmbTRlClsw
wW+pzsP5gHCv9mZO/x5RFeD0/7y/Afi5Sx3VfMny/s/Ao5W/1av5Vg/T5WGdd7lg
rppp/xPgDFGDPgf8qDX+t09Y8d2ZRfzANpRth7in2DIYH+TqPhTARBbmzqO2KIc3
iVYpVdn0P+E6JlprLWv1tyLr/clsleOYroi8iuYoTzF2Ox1Nxlgk
-----END CERTIFICATE-----"""

    val keyPem = """-----BEGIN PRIVATE KEY-----
MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCpsm1WSEDW/+8U
9/BBRpTCLlDA7Wc8o5ut9vfJx1usCkGHUYI2NPUlQI8AAfjcfagbi3ugUPEpuEK7
vR5y4IlN26ur1t+wzvYyhIDN1W9y0gUO+MRWIkFi+kPlPPLQfmN05VmAs9P9NlHW
M3rJ6ywC255mqWqUmcsUn8zVWpW8FJ5rVd8PNS9jdoV3jKiTgcP/s9g3pjtMN2uP
abK3p5gANzowsJbMPPzXxgh8VF/0mC3MycPy86h+qof+FiftTKJe7zXHx1m7q0pQ
fz1U+REwzsu9+6NfrRD4GeKNBxEZWVghsAOZ8PUuyl7coPfBFCZx9HdxR7MzbGVV
8LWzYXk9AgMBAAECggEALGov5dJZbixkZgeS0XLown4e0rAQQjXMLFckeaJ9IuU4
S3OQ8iEgPJTOGMFrYkJdOoBiZg5pYHMCvGJ+UrDkHwWsw/gkagFxPL2jfLwtRE8l
1cpUkPq8RGyeUqSGJP2/iDvzFhV7GZ4bA0ZMEAdGaKUUq2gSJjyug4JjuT/RSv1p
KuxElOAoz7SIvIFVw4hmdcKaYac+5X78fL9qaxP1xjUrMNFY7MokLs2XKx3HCzOm
SETAhb7qcK12XfBfuGvl8Op8hsZzJUBGsVaQH51wYbjalOwLL4ttA6QdDI403v0l
j+N5gQZwsbiigydq67Awn/MLGRcxyIALhBlH66ilgQKBgQDf3dEuMfrCJEAHgFAZ
aFLLUBhPGamQozVKetHGj4BWW1QCGA6R3OS9KVBmyouiI7SIXyYDjNN5YJSvr5qm
1HCa96O1qE5sh4WaduBi56PtXHz7AYDrRmgOEdbz1tMfuSYiaCIkWBXi23rX2ktX
1RniXbok76/alskvKiVPLVwAHQKBgQDCDhnxfDFm75XiBOBhqnUKxaZtqspRbjFO
Sm11tWh6jHjQ/3ohQnVSdRQCyGFyVAFtpEcadfvih/I/1PTBi05np4YK7ozaGtQ3
AxtYty+AGuyEq13Wr2akj4iD30V1N0bbcit0Pbj1VvKDryGkO79W3ewpdjsyW4Jz
u4VBSaxToQKBgEjUeD4oImVQt8f49ZYee3kLeK0bMzAL+MVfSanoe58cnSsFOpA3
pe7oZvDaCi1Yol4PXmWfRhlh012Iqq6FxJCV6huqQsFKIawL6poitBD/muVqKs/g
GvAg26Z+iDb03DQrXpgmVBB2yPM6YHKVsJMVXP6xP6vEjHUcqACnSBv9AoGAUpON
lqUyoIrzTOqmUOkoRR02ZRR4Y45wjNA/JAe+HegIwWb1oZGWOzB3A3ghf3Uf5ngx
iaELPqp6+46zWGjfRFyVPbGlXmpsQ7yetnG9VNYgL5R8qg6Zrw0lGni4JdkP3fIH
a7+YJU6KhF7SAgEqutxU0o/tQNCQAo+ZdN7U5gECgYAN2wpwTjVp0p9r5Elnqcri
cioskLvaPSrMesm/xvTH0kf7PjegqiMxjsyjAH03r7biUzUbhPyIok8X/IRBUtT9
gCMLSqH5IpwUytOovPcPCZITEEEg88jExQosCVN1OH52Bn0fIxSzB4Cr2ctEC5Eb
ea9gMds/F3MMoSAzy9NnAA==
-----END PRIVATE KEY-----"""

    return arrayOf(MHD_OPTION_HTTPS_MEM_KEY, keyPem, MHD_OPTION_HTTPS_MEM_CERT, certPem)
}

fun main(args: Array<String>) {
    val cliOptions = listOf(
            OptionDescriptor(OptionType.INT, "p", "port", "Port to use", "8080"),
            OptionDescriptor(OptionType.BOOLEAN, "h", "help", "Usage info"),
            OptionDescriptor(OptionType.BOOLEAN, "d", "daemon", "Run as daemon"),
            OptionDescriptor(OptionType.STRING, "a", "admin", "Secret for admin access"),
            OptionDescriptor(OptionType.BOOLEAN, "s", "https", "Use HTTPS")
    )
    var port = 8080
    var isDaemon = false
    var secret: String? = null
    var isHttps = false
    parseOptions(cliOptions, args).forEach {
        when (it.descriptor?.longName) {
            "port" -> port = it.intValue
            "daemon" -> isDaemon = true
            "help" -> showUsage(makeUsage(cliOptions))
            "admin" -> secret = it.stringValue
            "https" -> isHttps = true
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
        dbMain.execute("REPLACE INTO auth (user, secret) VALUES ('root', '$secret')")
    }

    // Was MHD_USE_INTERNAL_POLLING_THREAD or MHD_USE_AUTO or MHD_USE_ERROR_LOG
    val options = MHD_USE_POLL_INTERNALLY or (if (isHttps) MHD_USE_SSL else 0)
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
                val machine = MHD_lookup_connection_value(connection, MHD_GET_ARGUMENT_KIND, "machine") ?. toKString() ?: "?"
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
    *additionalOptions(isHttps),
    MHD_OPTION_END)

    if (daemon == null) {
        println("Cannot start daemon")
        exitProcess(2)
    }
    if (isDaemon) {
        println("Server started at http://localhost:$port going to background...")
        while (true) {
            kommon.usleep(1000000)
        }
    } else {
        println("Server started, connect to http://localhost:$port, press Enter to exit...")
        readLine()
    }
    MHD_stop_daemon(daemon)
    dbMain.close()
}
