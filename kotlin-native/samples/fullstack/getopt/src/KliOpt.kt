package kliopt

enum class OptionType(val hasParameter: Boolean) {
    BOOLEAN(false),
    STRING(true),
    INT(true)
}

data class OptionDescriptor(
        val type: OptionType,
        val shortName: String,
        val longName: String? = null,
        val description: String? = null,
        val defaultValue: String? = null)

class OptionError(text: String) : Error(text)

class ParsedOption(val descriptor: OptionDescriptor?, val value: String? = null) {
    val intValue: Int
        get() {
            if (descriptor != null && descriptor.type != OptionType.INT)
                throw OptionError("Incorrect option value, must be an int")
            return value!!.toInt()
        }

    val stringValue: String
        get() {
            if (descriptor != null && descriptor.type != OptionType.STRING)
                throw OptionError("Incorrect option value, must be a string")
            return value!!
        }

    val booleanValue: Boolean
        get() {
            if (descriptor != null && descriptor.type != OptionType.BOOLEAN)
                throw OptionError("Incorrect option value, must be a boolean")
            return value == "true"
        }
}

fun parseOptions(description: List<OptionDescriptor>, args: Array<String>): List<ParsedOption> {
    var index = 0

    val result = mutableListOf<ParsedOption>()

    val optmap = mutableMapOf<String, OptionDescriptor>()
    val inited = mutableMapOf<OptionDescriptor, Boolean>()
    description.forEach {
        optmap[it.shortName] = it
        if (it.longName != null) {
            optmap[it.longName] = it
        }
    }

    var afterOptions = false
    while (index < args.size) {
        val arg = args[index]
        if (afterOptions) {
            result += ParsedOption(null, arg)
        } else if (arg == "--") {
            afterOptions = true
        } else if (arg.startsWith('-')) {
            val descriptor = optmap.get(arg.substring(1))
            if (descriptor != null) {
                inited[descriptor] = true
                if (descriptor.type.hasParameter) {
                    if (index < args.size - 1) {
                        result += ParsedOption(descriptor, args[index + 1])
                        index++
                    } else {
                        // An error, option with value without value.
                        throw OptionError("No value for $descriptor")
                    }
                } else {
                    result += ParsedOption(descriptor, "true")
                }
            } else {
                throw OptionError("Unknown option $arg")
            }
        } else {
            result += ParsedOption(null, arg)
        }
        index++
    }

    description.forEach {
        if (inited[it] == null && it.defaultValue != null) {
            // Not inited, append default value if needed.
            result += ParsedOption(it, it.defaultValue)
        }
    }

    return result
}

fun makeUsage(options: List<OptionDescriptor>): String {
    val result = StringBuilder()
    result.append("Usage:\n")
    options.forEach {
        result.append("    -${it.shortName}")
        it.longName?.let { result.append(", -${it}") }
        it.defaultValue?.let { result.append(" [${it}]") }
        result.append(" -> ${it.description}\n")
    }
    return result.toString()
}