import kjson.*
import kliopt.*
import kurl.*
import kommon.machineName
import kotlin.system.exitProcess

fun showUsage(message: String) {
    println(message)
    exitProcess(1)
}

fun main(args: Array<String>) {
    var server: String? = null
    var name: String = ""
    var command: String? = null
    val options = listOf(
            OptionDescriptor(OptionType.STRING, "s", "server", "Server to connect", "http://localhost:8080"),
            OptionDescriptor(OptionType.STRING, "n", "name", "User name", "CLI user"),
            OptionDescriptor(OptionType.BOOLEAN, "h", "help", "Usage info"),
            OptionDescriptor(OptionType.STRING, "c", "command", "Command to issue", "stats"),
            OptionDescriptor(OptionType.STRING, "p", "password", "Password for admin access")
    )
    var password = ""
    parseOptions(options, args).forEach {
        when (it.descriptor?.longName) {
            "server" -> server = it.stringValue
            "name" -> name = it.stringValue
            "command" -> command = it.stringValue
            "password" -> password = it.stringValue
            "help" -> showUsage(makeUsage(options))
        }
    }
    val machine = machineName()
    println("Connecting to $server as $name from $machine")
    val kurl = KUrl("cookies.txt")
    val url = "$server/json/$command"
    withUrl(kurl) { it.fetch(url,
            mapOf("name" to name, "client" to "cli", "machine" to machine, "password" to password)) {
        content -> withJson(content) {
                println("Got $it, my color is ${it.getInt("color")}")
            }
        }
    }
}
