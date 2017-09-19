import kjson.*
import kliopt.*
import kurl.*
import kommon.machineName

fun main(args: Array<String>) {
    var server: String? = null
    var name: String? = null
    var command: String? = null
    val options = listOf(
            OptionDescriptor(OptionType.STRING, "s", "server", "Server to connect", "http://localhost:1111"),
            OptionDescriptor(OptionType.STRING, "n", "name", "User name", "CLI user"),
            OptionDescriptor(OptionType.BOOLEAN, "h", "help", "Usage info"),
            OptionDescriptor(OptionType.STRING, "c", "command", "Command to issue", "stats")
    )
    parseOptions(options, args).forEach {
        when (it.descriptor.longName) {
            "server" -> server = it.stringValue
            "name" -> name = it.stringValue
            "command" -> command = it.stringValue
            "help" -> println(makeUsage(options))
        }
    }
    val machine = machineName()
    println("Connecting to $server as $name from $machine")
    var kurl = KUrl("cookies.txt")
    val url = "$server/json/$command?name=${kurl.escape(name!!)}&client=cli&machine=${kurl.escape(machine)}"
    withUrl(kurl) { it.fetch(url) {
        content -> withJson(content) {
            println("Got $it, my color is ${it.getInt("color")}")
        }
      }
    }
}
