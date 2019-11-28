import kjson.*
import ksqlite.*
import kliopt.*
import microhttpd.*
import kommon.*
import kotlinx.cinterop.toKString
import platform.posix.*

object Finder {
    const val dbNameGames = "finderGames"
    const val dbNameHints = "finderHints"
    const val dbNameFacts = "finderFacts"
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
            CREATE TABLE IF NOT EXISTS ${dbNameHints} (
                code INTEGER,
                hint TEXT,
                valid INTEGER
            );
            CREATE TABLE IF NOT EXISTS ${dbNameFacts}             (
                fact TEXT,
                valid INTEGER
            );
            CREATE TABLE IF NOT EXISTS ${dbNameBeacons} (
                id INTEGER PRIMARY KEY,
                code INTEGER,
                name VARCHAR(255) NOT NULL UNIQUE,
                hashedName VARCHAR(255),
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
                cookie VARCHAR(64) NOT NULL,
                uniq VARCHAR(255) NOT NULL UNIQUE,
                name TEXT,
                timestamp DATE,
                game INTEGER,
                place INTEGER
            );
    """.trimIndent()

    fun success(json: KJsonObject) {
        json.setString("result", "OK")
    }

    fun failure(json: KJsonObject) {
        json.setString("result", "FAILED")
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
            val hashedName = name.cityHash64().toString(16)
            val threshold = beacon[2].toInt()
            val active = beacon[3].toInt()

            db.execute("INSERT INTO $dbNameBeacons (code, name, hashedName, threshold, active) VALUES " +
                    "($code, '$name', '$hashedName', $threshold, $active)")
            success(json)
        }
    }

    fun addHint(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        val hintParam =
                MHD_lookup_connection_value(session.http, MHD_GET_ARGUMENT_KIND, "hint") ?. toKString() ?: ""
        if (hintParam.isNotEmpty()) {
            val codeHint = hintParam.split("|||").map { db.escape(it)}
            db.execute("INSERT INTO $dbNameHints (code, hint, valid) VALUES "+
                    "(${codeHint[0].toInt()}, '${codeHint[1]}', 1)")
            success(json)
        }
    }

    fun addFact(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        val factParam =
                MHD_lookup_connection_value(session.http, MHD_GET_ARGUMENT_KIND, "fact") ?. toKString() ?: ""
        if (factParam.isNotEmpty()) {
            val fact = db.escape(factParam)
            db.execute("INSERT INTO $dbNameFacts (fact, valid) VALUES ('$fact', 1)")
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
        var currentGame = 0
        var winnerCount = -1
        var winnerMessage = "You won"
        var loserMessage = "You lose"
        db.execute("SELECT game,winnerCount,winnerMessage,loserMessage FROM $dbNameGames WHERE current = 'true'") { _, data ->
            currentGame = data[0].toInt()
            winnerCount = data[1].toInt()
            winnerMessage = data[2]
            loserMessage = data[3]
            0
        }
        var actuallyFound = 0
        db.execute("SELECT COUNT(*) FROM $dbNameResults WHERE cookie = '$cookie' and game = $currentGame")
        { _, data ->
            actuallyFound = data[0].toInt() + 1
            0
        }
        if (actuallyFound < winnerCount) {
            failure(json)
            return
        }
        var place = 1
        db.execute("SELECT COUNT(*) FROM $dbNameWinners WHERE game = $currentGame") { _, data ->
            place = data[0].toInt() + 1
            0
        }
        // We use `uniq` field to make sure only single record of winning during one game could exist.
        try {
            db.execute("INSERT INTO $dbNameWinners (cookie, name, timestamp, game, place, uniq) " +
                    " VALUES ('$cookie', '${db.escape(session.name)}', DateTime('now'), $currentGame, $place, '${cookie}_${currentGame}')")
        } catch (e: KSqliteError) {
            println(e)
            failure(json)
            return
        }
        val isWinner = place <= winnerCount
        json.setString("message", if (isWinner) winnerMessage else loserMessage)
        json.setInt("winner", if (isWinner) 1 else 0)
        json.setInt("place", place)
        json.setInt("winnerCount", winnerCount)
        success(json)
    }

    fun config(db: KSqlite, session: Session, json: KJsonObject) {
        val hintsArray = KJsonArray()
        var hints = 0
        db.execute("SELECT code, hint FROM $dbNameHints WHERE valid = 1") { _, data ->
            val record = KJsonObject()
            record.setInt("code", data[0].toInt())
            record.setString("hint", data[1])
            hintsArray.appendObject(record)
            hints++
            0
        }
        val factsArray = KJsonArray()
        db.execute("SELECT fact FROM $dbNameFacts WHERE valid = 1") { _, data ->
            val record = KJsonObject()
            factsArray.appendString(data[0])
            0
        }
        var activeBeacons = 0
        db.execute("SELECT COUNT(*) FROM $dbNameBeacons WHERE active <> 0") { _, data ->
            activeBeacons = data[0].toInt()
            0
        }
        var winnerCount = 0
        db.execute("SELECT winnerCount FROM $dbNameGames WHERE current = 'true'") { _, data ->
            winnerCount = data[0].toInt()
            0
        }
        json.setInt("index", if (hints > 0) rand() % hints else 0)
        json.setInt("activeBeacons", activeBeacons)
        json.setInt("winnerCount", winnerCount)
        json.setArray("hints", hintsArray)
        json.setArray("facts", factsArray)
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
            db.execute("SELECT code, hashedName, threshold from ${dbNameBeacons} WHERE hashedName IN ($set)") {
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
                val allFound = discovered.map {it.first}.toMutableSet()
                allFound.addAll(alreadyDiscovered)
                allFound.forEach {
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
