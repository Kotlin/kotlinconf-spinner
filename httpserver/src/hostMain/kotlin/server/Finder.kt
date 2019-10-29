import kjson.*
import ksqlite.*
import kliopt.*
import microhttpd.*
import kommon.*
import kotlinx.cinterop.toKString
import platform.posix.*

object Finder {
    const val dbNameGames = "finderGames"
    const val dbNameUsers = "finderUsers"
    const val dbNameQuestions = "finderQuestions"
    const val dbNameBeacons = "finderBeacons"
    const val dbNameResults = "finderResults"
    const val dbNameWinners = "finderWinners"

    val dbCreateCommand = """
            CREATE TABLE IF NOT EXISTS ${dbNameGames} (
                game INTEGER PRIMARY KEY,
                current BOOLEAN NOT NULL,
                status INTEGER NOT NULL,
                startTime DATE,
                endTime DATE,
                winnerCount INTEGER,
                winnerMessage TEXT,
                loserMessage TEXT
            );
            CREATE TABLE IF NOT EXISTS ${dbNameUsers} (
                cookie VARCHAR(64) NOT NULL NULL PRIMARY KEY,
                name VARCHAR(255),
                timestamp DATE
            );
            CREATE TABLE IF NOT EXISTS ${dbNameQuestions} (
                code INTEGER,
                question TEXT,
                hint TEXT,
                valid INTEGER
            );
            CREATE TABLE IF NOT EXISTS ${dbNameBeacons} (
                id INTEGER PRIMARY KEY,
                code INTEGER,
                name VARCHAR(255) NOT NULL UNIQUE,
                threshold INTEGER,
                active INTEGER
            );
            CREATE TABLE IF NOT EXISTS ${dbNameResults} (
                cookie VARCHAR(64) NOT NULL,
                code INTEGER,
                signal INTEGER,
                game INTEGER,
                timestamp DATE
            );
            CREATE TABLE IF NOT EXISTS ${dbNameWinners} (
                cookie VARCHAR(64) NOT NULL NULL PRIMARY KEY,
                timestamp DATE,
                game INTEGER,
                place INTEGER
            );
    """.trimIndent()

    fun success(json: KJsonObject) {
        json.setString("result", "OK")
    }

    fun start(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        val gameParam =
                MHD_lookup_connection_value(session.http, MHD_GET_ARGUMENT_KIND, "start") ?. toKString() ?: ""
        val game = gameParam.split("|||").map { db.escape(it)}
        if (game.size != 3) throw Error("Invalid game config: ${game.size} fields: $gameParam")
        val winnerCount = game[0].toInt()
        db.execute("UPDATE $dbNameGames SET status=0,current='false',endTime=DateTime('now') WHERE current='true'")
        db.execute("INSERT INTO $dbNameGames (current, status, startTime, winnerCount, winnerMessage, loserMessage) "+
                "VALUES ('true', 1, DateTime('now'), $winnerCount, '${game[1]}', '${game[2]}')")
        success(json)
    }

    fun stop(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        db.execute("UPDATE $dbNameGames SET status = 0,endTime = DateTime('now') WHERE current = 'true'")
        success(json)
    }

    fun addBeacon(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        val beaconParam =
                MHD_lookup_connection_value(session.http, MHD_GET_ARGUMENT_KIND, "beacon") ?. toKString() ?: ""
        if (beaconParam.isNotEmpty()) {
            val beacon = beaconParam.split(",")
            val code = beacon[0].toInt()
            val name = db.escape(beacon[1])
            val threshold = beacon[2].toInt()
            val active = beacon[3].toInt()

            db.execute("INSERT INTO $dbNameBeacons (code, name, threshold, active) VALUES ($code, '$name', $threshold, $active)")
            success(json)
        }
    }

    fun addQuestion(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        val questionParam =
                MHD_lookup_connection_value(session.http, MHD_GET_ARGUMENT_KIND, "question") ?. toKString() ?: ""
        if (questionParam.isNotEmpty()) {
            val questionHint = questionParam.split("|||").map { db.escape(it)}
            db.execute("INSERT INTO $dbNameQuestions (code, question, hint, valid) VALUES "+
                    "(${questionHint[0].toInt()}, '${questionHint[1]}', '${questionHint[2]}', 1)")
            success(json)
        }
    }

    fun status(db: KSqlite, session: Session, json: KJsonObject) {
        var hasCurrent = false
        db.execute("SELECT startTime, status FROM $dbNameGames WHERE current = 'true'") { _, data ->
            json.setString("startTime", data[0])
            json.setInt("status", data[1].toInt())
            hasCurrent = true
            0
        }
        if (!hasCurrent) {
            json.setString("startTime", "")
            json.setInt("status", 0)
        }
        success(json)
    }

    fun register(db: KSqlite, session: Session, json: KJsonObject) {
        val cookie = session.cookie
        db.execute("UPDATE $dbNameUsers SET name = ${session.name} WHERE secret = '$cookie'")
        var currentGame = 0
        db.execute("SELECT id FROM $dbNameGames WHERE current = 'true'") { _, data ->
            currentGame = data[0].toInt()
            0
        }
        var place = 1
        db.execute("SELECT COUNT(*) FROM $dbNameWinners WHERE game = $currentGame") { _, data ->
            place = data[0].toInt() + 1
            0
        }
        println("place would be $place")
        // TODO: add place checker!
        //db.execute("UPDATE $dbNameWinners WHERE game = $currentGame AND cookie = $cookie")
        success(json)
    }

    fun config(db: KSqlite, session: Session, json: KJsonObject) {
        val config = KJsonArray()
        var questions = 0
        db.execute("SELECT code, question, hint FROM $dbNameQuestions WHERE valid = 1") { _, data ->
            val record = KJsonObject()
            record.setInt("code", data[0].toInt())
            record.setString("question", data[1])
            record.setString("hint", data[2])
            config.appendObject(record)
            questions++
            0
        }
        var activeBeacons = 0
        db.execute("SELECT COUNT(*) FROM $dbNameBeacons WHERE active <> 0") { _, data ->
            activeBeacons = data[0].toInt()
            0
        }
        json.setInt("index", if (questions > 0) rand() % questions else 0)
        json.setInt("active", activeBeacons)
        json.setArray("config", config)
        success(json)
    }

    data class BeaconInfo(val name: String, val signal: Int)

    fun proximity(db: KSqlite, session: Session, json: KJsonObject) {
        val proximity =
                MHD_lookup_connection_value(session.http, MHD_GET_ARGUMENT_KIND, "proximity") ?. toKString() ?: ""
        if (proximity.isNotEmpty()) {
            val data = proximity.split(",").map { it.split(":").let {
                    BeaconInfo(it[0], it[1].toInt()) } }
            val set = data.joinToString(separator = ", ") { "'${db.escape(it.name)}'" }
            val strengths = data.associate { it.name to it.signal }
            val discovered = mutableSetOf<Pair<Int, Int>>()
            val near = mutableSetOf<Pair<Int, Int>>()
            db.execute("SELECT code, name, threshold from ${dbNameBeacons} WHERE name IN ($set)") {
                _, data ->
                val code = data[0].toInt()
                val name = data[1]
                val threshold = data[2].toInt()
                strengths[name]?.let { strength ->
                    if (strength >= threshold) {
                        discovered.add(code to strength)
                    } else {
                        val diff = (threshold - strength).toDouble()
                        near.add(code to (100.0 - diff).toInt())
                    }
                }
                0
            }
            // Record discovery in DB.
            var gameId = 0
            db.execute("SELECT game FROM $dbNameGames WHERE current = 'true'") { _, data ->
                gameId = data[0].toInt()
                0
            }
            // Avoid storing, if already discovered in this game by this user.
            val alreadyDiscovered = mutableSetOf<Int>()
            db.execute("SELECT code FROM $dbNameResults WHERE game=$gameId AND cookie='${session.cookie}'") {
                _, data ->
                alreadyDiscovered += data[0].toInt()
                0
            }
            discovered.filterNot { it.first in alreadyDiscovered }.forEach {
                db.execute("INSERT INTO $dbNameResults (cookie, code, signal, game, timestamp) VALUES " +
                        "('${session.cookie}', ${it.first}, ${it.second}, $gameId, DateTime('now'))")
            }
            val discoveredJson = KJsonArray().apply {
                discovered.forEach {
                    appendInt(it.first)
                }
                alreadyDiscovered.forEach {
                    appendInt(it)
                }
            }
            val nearJson = KJsonArray().apply {
                near.filterNot{it.first in alreadyDiscovered}.forEach {
                    appendObject(KJsonObject().also { obj ->
                        obj.setInt("code", it.first)
                        obj.setInt("strength", it.second)
                    })
                }
            }
            json.setArray("discovered", discoveredJson)
            json.setArray("near", nearJson)
        }
        success(json)
    }
}
