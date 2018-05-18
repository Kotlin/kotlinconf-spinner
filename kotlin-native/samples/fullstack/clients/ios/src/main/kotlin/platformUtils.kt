/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import kotlinx.cinterop.*
import platform.darwin.NSObject
import platform.GameKit.*
import platform.Foundation.*

class StatsFetcherImpl : StatsFetcher {
    private var mostRecentFetched: Stats? = null
    private var requestInProgress = false

    private val server = "http://kotlin-demo.kotlinconf.com:8080"
    private val name = "iOS_user"
    private val machine = "iPhone" // TODO

    override fun asyncFetch() {
        asyncRequest("$server/json/stats?name=$name&client=iOS&machine=$machine")
    }

    override fun asyncTryClickAndFetch(): Boolean {
        return asyncRequest("$server/json/click")
        // TODO: handle if the request will fail.
    }

    private fun asyncRequest(url: String): Boolean {
        if (requestInProgress) return false
        requestInProgress = true

        val delegate = object : NSObject(), NSURLSessionDataDelegateProtocol {
            val receivedData = NSMutableData()

            override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
                receivedData.appendData(didReceiveData)
            }

            override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
                requestInProgress = false // Only main thread accesses the fetcher, so it's safe to clear the flag here.

                val response = task.response
                if (response == null || (response as NSHTTPURLResponse).statusCode.toInt() != 200) {
                    return
                }

                if (didCompleteWithError != null) {
                    return
                }

                // TODO: report errors.

                val receivedStats = parseJsonResponse(receivedData)
                if (receivedStats != null) {
                    mostRecentFetched = receivedStats
                    receivedStats.reportToGameCenter()
                }
            }
        }

        val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration(),
                delegate,
                delegateQueue = NSOperationQueue.mainQueue()
        )

        session.dataTaskWithURL(NSURL(string = url)).resume()

        return true
    }

    override fun getMostRecentFetched(): Stats? = this.mostRecentFetched
}

private fun Map<String,*>.intKey(key: String): Int {
  val value = this[key]!!
  return (value as Number).toInt()
}

private fun parseJsonResponse(data: NSData): Stats? {
    val dict = (NSJSONSerialization.JSONObjectWithData(data, 0, null) as? Map<String, *>)
       ?: throw Error("JSON parsing error")

    val myTeamIndex = dict.intKey("color") - 1
    val myTeam = Team.values()[myTeamIndex]

    val myConribution = dict.intKey("contribution")
    val status = dict.intKey("status")
    val winner = dict.intKey("winner")
    val colors = dict["colors"] as List<*>
    val counts = IntArray(Team.count)
    for (i in 0 until colors.size) {
        val element = colors[i] as Map<String,*>
        counts[i] = element.intKey("counter")
    }

    return Stats(counts, myTeam, myConribution, status, winner != 0)
}

fun Stats.reportToGameCenter() {
    if (!GKLocalPlayer.localPlayer().isAuthenticated()) return

    val gkScore = GKScore("main")
    gkScore.value = this.myContribution.signExtend()
    gkScore.context = 0

    val gkScoreArray = NSArray.arrayWithObject(gkScore)

    GKScore.reportScores(gkScoreArray, withCompletionHandler = null)
}

// TODO: consider not using logError function on Android.
fun logError(message: String) {
    println(message)
}
