import kotlinx.cinterop.*
import platform.android.*
import kotlin.system.*
import platform.posix.*
import kommon.machineName
import kurl.*
import kjson.*
import kotlin.native.concurrent.*

class StatsFetcherImpl(val nativeActivity: ANativeActivity): StatsFetcher {
    private val server = "http://kotlin-demo.kotlinconf.com:8080"
    private val name = "Android user"

    private fun cookiesFileName() = "${nativeActivity.internalDataPath!!.toKString()}/cookies.txt"

    private val worker = Worker.start()
    private var future: Future<Any>? = null
    private var mostRecentlyFetched: Stats? = null

    override fun asyncFetch() {
        val machine = machineName()
        val kurl = KUrl(cookiesFileName())
        val url = "$server/json/stats?name=${kurl.escape(name)}&client=android&machine=${kurl.escape(machine)}"
        kurl.close()
        asyncRequest(url)
    }

    override fun asyncTryClickAndFetch(): Boolean {
        return asyncRequest("$server/json/click")
    }

    override fun getMostRecentFetched(): Stats? {
        val future = this.future
        if (future != null && future.state == FutureState.COMPUTED) {
            future.consume {
                if (it is String)
                    logError(it)
                else {
                    if (it !is KJsonObject)
                        logError("A KJsonObject expected but was $it")
                    else {
                        val myColor = it.getInt("color") - 1
                        val myContribution = it.getInt("contribution")
                        val status = it.getInt("status")
                        val winner = it.getInt("winner")
                        val colors = it.getArray("colors")
                        val counts = IntArray(colors.size)
                        counts.indices.forEach {
                            val obj = colors.getObject(it)
                            val color = obj.getInt("color")
                            val counter = obj.getInt("counter")
                            logInfo("Color: $color, counter = $counter")
                            counts[color - 1] = counter
                        }
                        it.close()
                        mostRecentlyFetched = Stats(counts, enumValues<Team>()[myColor], myContribution, status, winner != 0)
                    }
                }
            }
        }
        return mostRecentlyFetched
    }

    private class WorkerArgument(val url: String, val cookiesFileName: String)

    private fun asyncRequest(url: String): Boolean {
        if (future?.state == FutureState.SCHEDULED) return false
        future = worker.execute(TransferMode.SAFE, { WorkerArgument("$url", cookiesFileName()) }) {
            val kurl = KUrl(it.cookiesFileName)
            val url = it.url
            try {
                withUrl(kurl) {
                    var result: String? = null
                    it.fetch(url) {
                        result = it
                    }
                    KJsonObject(result!!)
                }
            } catch (error: KUrlError) {
                "Network problem: $error"
            } catch (t: Throwable) {
                "Exception: $t"
            } finally {
                kurl.close()
            }
        }
        return true
    }
}
