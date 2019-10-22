import kjson.*
import ksqlite.*
import kliopt.*
import microhttpd.*
import kommon.*
import platform.posix.*

object Spinner {
    const val dbNameSessions = "sessions"
    const val dbNameColors = "colors"
    const val dbNameGames = "games"

    val dbCreateCommand = """
        CREATE TABLE IF NOT EXISTS ${dbNameColors}        (
            color INTEGER PRIMARY KEY AUTOINCREMENT,
            counter INTEGER
        );
        CREATE TABLE IF NOT EXISTS ${dbNameGames}        (
            current BOOLEAN NOT NULL,
            status INTEGER NOT NULL,
            winner INTEGER,
            startTime DATE,
            endTime DATE
        );
    """.trimIndent()

    fun stats(db: KSqlite, session: Session, json: KJsonObject) {
        val colors = KJsonArray()
        db.execute("SELECT counter, color FROM colors") { _, data ->
            val record = KJsonObject()
            record.setInt("counter", data[0].toInt())
            record.setInt("color", data[1].toInt())
            colors.appendObject(record)
            0
        }
        db.execute("SELECT startTime, status FROM games WHERE current = 'true'") { _, data ->
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
        db.execute("SELECT status FROM games WHERE current = 'true'") { _, data ->
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
            error(json, session, "Unauthorized")
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
            error(json, session, "Unauthorized")
            return
        }
        db.execute("UPDATE games SET status = 1, winner=NULL WHERE current = 'true'")
        db.execute("UPDATE sessions SET winner = 0")
        stats(db, session, json)
    }

    fun setWinner(db: KSqlite, endGame: Boolean) {
        var winner = -1
        db.execute("SELECT color FROM colors ORDER BY counter DESC LIMIT 1") { _, data ->
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
        db.execute("SELECT cookie FROM sessions WHERE color=$winner ORDER BY counter DESC LIMIT 10") { _, data ->
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
            error(json, session, "Unauthorized")
            return
        }
        setWinner(db, false)
        stats(db, session, json)
    }

    fun stop(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        setWinner(db, true)
        stats(db, session, json)
    }

    fun show(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        db.execute("UPDATE games SET status = 2 WHERE current = 'true'")
        stats(db, session, json)
    }

    fun hide(db: KSqlite, session: Session, json: KJsonObject) {
        if (!session.isAdmin(db)) {
            error(json, session, "Unauthorized")
            return
        }
        db.execute("UPDATE games SET status = 1 WHERE current = 'true'")
        stats(db, session, json)
    }
}