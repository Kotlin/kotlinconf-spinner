import kotlin.native.concurrent.FutureState
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kurl.*
import kjson.*
import platform.posix.sleep
import kotlin.native.concurrent.Future


private class WorkerArgument(val url: String, val cookiesFileName: String)

data class Tttt(val foo: String, val i: Int)

private fun asyncRequest(worker: Worker): Future<Any> {
//    val future = worker.execute(TransferMode.SAFE, { WorkerArgument("$url", cookiesFileName()) }) {
    val future = worker.execute(TransferMode.SAFE, { WorkerArgument("http://kotlin-demo.kotlinconf.com:8080/json/click", "cookies.txt") }) {
        val kurl = KUrl(it.cookiesFileName)
        val url = it.url
        println("Created Kurl: $kurl")
        val result = try {
            println("Before fetch")
            withUrl(kurl) {
                println("Started withKurl")
                var result: Any? = null
                println("Start fetch")
                it.fetch(url) {
                    println("Fetched")
                    //result = it
                    //result = Tttt(it, 42)
                    result = KJsonObject(it)
                    //result = "{\"startTime\": \"2018-09-17 10:41:15\"}"
                    //result = "{\"startTime\": \"2018-09-17 10:41:15\"}"
                    //result = "{\"startTime\": \"2018-09-17 10:41:15\"}" as Any
                }
                println("Before returning result: $result")
                result!!
            }
        } catch (error: KUrlError) {
            "Network problem: $error"
        } catch (t: Throwable) {
            "Exception: $t"
        } finally {
            kurl.close()
        }
        println("fetch finished: $result")
        result
    }
    return future
}

fun main() {
    val worker = Worker.start()
    val future = asyncRequest(worker)
    sleep(1U)
    println("tttt")
    println(future)
    future.consume {
        println(it)
    }
}