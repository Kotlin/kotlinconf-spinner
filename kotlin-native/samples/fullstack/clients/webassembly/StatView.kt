import html5.minimal.*
import kotlinx.wasm.jsinterop.*
import konan.internal.ExportForCppRuntime

object Model {
    val tupletSize = 5
    // Matching OpenGL demo colors:
    //val colors = arrayOf(Vector3(0.0f, 0.8f, 0.8f), Vector3(0.8f, 0.0f, 0.8f), Vector3(0.8f, 0.0f, 0.0f), Vector3(0.0f, 0.8f, 0.0f), Vector3(0.0f, 0.0f, 0.8f))
    val styles = arrayOf("#00cdcd", "#cd00cd", "#cd0000", "#00cd00", "#0000cd")

    val backLogSize = 100
    val colors = Array<Array<Int>>(backLogSize, {arrayOf(0, 0, 0, 0, 0)})

    var current = 0
    var maximal = 0

    fun push(new: Array<Int>) {
        assert(new.size == tupletSize)
        colors[current] = new
        current = (current+1).rem(backLogSize)

        new.forEach {
            if (it > maximal) maximal = it
        }
    }
}


class View(canvas: Canvas) {
    val ctx = canvas.getContext("2d");
    val rect = canvas.getBoundingClientRect();
    val rectLeft = rect.getInt("left")
    val rectTop = rect.getInt("top")
    val rectRight = rect.getInt("right")
    val rectBottom = rect.getInt("bottom")
    val rectWidth = rectRight - rectLeft
    val rectHeight = rectBottom - rectTop

    fun line(x: Int, y: Int, x2: Int, y2: Int, style: String) {
        ctx.beginPath()
        ctx.lineWidth = 4; // In pixels.
        ctx.setter("strokeStyle", style)
        ctx.moveTo(x, rectHeight - y)
        ctx.lineTo(x2, rectHeight - y2)
        ctx.stroke()
    }

    fun scale(x: Int, y: Int): Pair<Int, Int> {
        return Pair(x * rectWidth / Model.backLogSize, y * rectHeight / top)
    }

    val top: Int
        get() {
            // We still don't have logs and exps, oh well.
            // And we don't have libm for wasm either.
            var result = 100;
            while (result < Model.maximal) {
                // TODO: Do we expect click counter to overflow INT_MAX?
                // What shall we do then???
                result *= 10
            }

            return result
        }

    fun clean() {
        ctx.fillStyle = "#222222"
        ctx.fillRect(0, 0, rectWidth, rectHeight)
    }

    fun render() {
        clean()
        for (i in 0 until Model.tupletSize) {
            for (t in 0 until Model.backLogSize-2) {
                val index = ( Model.current + t).rem(Model.backLogSize-1)
                val (x1, y1) = scale(t, Model.colors[index][i])
                val (x2, y2) = scale(t+1, Model.colors[index+1][i])
                line( x1, y1, x2, y2, Model.styles[i])
            }
        }
    }
}

val html5 = Html5()
val document = html5.document
val canvas = document.getElementById("myCanvas").asCanvas;

fun loop() {
    html5.fetch("/json/stats") .then { args: ArrayList<JsValue> ->
        val response = Response(args[0])
        response.json()
    } .then { args: ArrayList<JsValue> ->
        val json = args[0]
        val myColor = json.getInt("color")
        val colors = JsArray(json.getProperty("colors"))
        assert(colors.size == Model.tupletSize)

        val tuplet = arrayOf<Int>(0, 0, 0, 0, 0)
        for (i in 0 until colors.size) {
            val color = colors[i].getInt("color")
            val counter = colors[i].getInt("counter")
            // Colors are numbered 1..5
            tuplet[color-1] = counter
        }
        Model.push(tuplet)
    } .then {
        View(canvas).render()
    }
}

fun main(args: Array<String>) {
    html5.setInterval(1000) {
        loop()
    }
}

