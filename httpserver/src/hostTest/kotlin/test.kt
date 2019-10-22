import kjson.KJsonObject
import ksqlite.KSqlite
import ksqlite.withSqlite
import kotlin.test.*
import platform.posix.unlink

val DB_PATH = "test.dblite"

fun makeTestSession(name: String, password: String, db: KSqlite, color: Int): Session {
    val freshBakery = "${rnd().toString(16)}${rnd().toString(16)}${rnd().toString(16)}"
    db.execute(
            "INSERT INTO sessions (cookie, color, name, counter, winner) VALUES ('$freshBakery', $color, '${db.escape(name)}', 0, 0)")
    return Session(color, name, freshBakery, password)
}

class ServerTest {

    @BeforeTest
    fun createDatabase() = withSqlite (DB_PATH) { db ->
        db.execute(createDbCommand)
        for (i in 0 until MAX_COLORS) {
            db.execute("INSERT INTO colors (counter) VALUES (0)")
        }
        db.execute("INSERT INTO auth (user, secret) VALUES ('root', 'root')")
    }

    @AfterTest
    fun removeDatabase() {
        unlink(DB_PATH)
    }

    inline fun assertColorHasCountAfterAction(color: Int, expectedCount: Int, action: (KJsonObject) -> Unit) =
            assertJson(action) {
                it.getArray("colors").getObject(color - 1).getInt("counter") == expectedCount
            }

    inline fun withJson(action: (KJsonObject) -> Unit): KJsonObject = KJsonObject().apply { action(this) }

    inline fun assertJsonCustom(action: (KJsonObject) -> KJsonObject, check: (KJsonObject) -> Unit) {
        val json = KJsonObject()
        val result = action(json)
        check(result)
    }

    inline fun assertJson(action: (KJsonObject) -> Unit, check: (KJsonObject) -> Unit) =
            assertJsonCustom({ action(it); it }, check)

    inline fun assertStats(db: KSqlite, session: Session, check: (KJsonObject) -> Unit) =
            assertJson({ Spinner.stats(db, session, it) }, check)

    @Test
    fun clickTest() = withSqlite(DB_PATH) { db ->
        val admin = makeSession("root", "root", db, null)
        val json = KJsonObject()
        Spinner.start(db, admin, json)

        val alice = makeSession("Alice",  "pass", db, null)
        var bob: Session
        do {
            bob = makeSession("Bob", "pass", db, null)
        } while(bob.color == alice.color)

        assertColorHasCountAfterAction(alice.color, 1) { Spinner.click(db, alice, it) }
        assertColorHasCountAfterAction(alice.color, 2) { Spinner.click(db, alice, it) }
        assertColorHasCountAfterAction(bob.color, 1) { Spinner.click(db, bob, it) }

        assertColorHasCountAfterAction(alice.color, 2) { Spinner.stats(db, bob, it) }
        assertColorHasCountAfterAction(bob.color, 1) { Spinner.stats(db, alice, it) }
    }

    @Test
    fun startStopTest() = withSqlite(DB_PATH) { db ->
        val admin = makeTestSession("root", "root", db, 1)
        withJson { Spinner.start(db, admin, it) }
        withJson { Spinner.click(db, admin, it) }
        assertStats(db, admin) {
            assertEquals(1, it.getInt("status"))
        }
        withJson { Spinner.stop(db, admin, it) }
        assertStats(db, admin) {
            assertEquals(0, it.getInt("status"))
            assertEquals(1, it.getInt("winner"))
            assertEquals(1, it.getInt("contribution"))
        }
        withJson { Spinner.start(db, admin, it) }
        assertStats(db, admin) {
            assertEquals(0, it.getInt("contribution"))
        }
    }

    @Test
    fun pauseResumeTest() = withSqlite(DB_PATH) { db ->
        val admin = makeTestSession("root", "root", db, 1)
        withJson { Spinner.start(db, admin, it) }
        withJson { Spinner.click(db, admin, it) }
        assertStats(db, admin) {
            assertEquals(1, it.getInt("status"))
        }
        withJson { Spinner.pause(db, admin, it) }
        assertStats(db, admin) {
            assertEquals(0, it.getInt("status"))
            assertEquals(1, it.getInt("winner"))
            assertEquals(1, it.getInt("contribution"))
        }
        withJson { Spinner.resume(db, admin, it) }
        assertStats(db, admin) {
            assertEquals(1, it.getInt("contribution"))
        }
    }

    @Test
    fun winnerTest() = withSqlite(DB_PATH) { db ->
        val admin = makeTestSession("root", "root", db, 1)
        val alice = makeTestSession("Alice", "pass", db, 1)
        val bob = makeTestSession("Bob", "pass", db, 2)

        withJson {
            Spinner.start(db, admin, it)
            Spinner.click(db, admin, it)
            Spinner.click(db, admin, it)
            Spinner.click(db, alice, it)
            Spinner.click(db, bob, it)
            Spinner.click(db, bob, it)
            Spinner.stop(db, admin, it)
        }
        assertStats(db, admin) {
            assertEquals(2, it.getInt("contribution"))
            assertEquals(1, it.getInt("winner"))
        }

        assertStats(db, alice) {
            assertEquals(1, it.getInt("contribution"))
            assertEquals(1, it.getInt("winner"))
        }

        assertStats(db, bob) {
            assertEquals(2, it.getInt("contribution"))
            assertEquals(0, it.getInt("winner"))
        }
    }
}