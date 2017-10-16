import html5.minimal.*
import kotlinx.wasm.jsinterop.*
import konan.internal.ExportForCppRuntime

object Model {
    val tupleSize = 5
    // Matching OpenGL demo colors:
    //val colors = arrayOf(Vector3(0.0f, 0.8f, 0.8f), Vector3(0.8f, 0.0f, 0.8f), Vector3(0.8f, 0.0f, 0.0f), Vector3(0.0f, 0.8f, 0.0f), Vector3(0.0f, 0.0f, 0.8f))
    val styles = arrayOf("#00cdcd", "#cd00cd", "#cd0000", "#00cd00", "#0000cd")

    val backLogSize = 100
    private val backLog = IntArray(backLogSize * tupleSize, {0})
    private fun offset(time: Int, color: Int) = time * tupleSize + color

    var current = 0
    var maximal = 0

    fun colors(time: Int, color: Int): Int = backLog[offset(time, color)]

    fun tuple(time: Int) = backLog.slice(time * tupleSize .. (time + 1) * tupleSize - 1)

    fun push(new: Array<Int>) {
        assert(new.size == tupleSize)
        
        new.forEachIndexed { index, it -> 
            backLog[offset(current, index)] = it 
        }
        current = (current+1) % backLogSize

        new.forEach {
            if (it > maximal) maximal = it
        }
    }
}


class View(canvas: Canvas) {
    val context = canvas.getContext("2d");
    val rect = canvas.getBoundingClientRect();
    val rectLeft = rect.getInt("left")
    val rectTop = rect.getInt("top")
    val rectRight = rect.getInt("right")
    val rectBottom = rect.getInt("bottom")
    val rectWidth = rectRight - rectLeft
    val rectHeight = rectBottom - rectTop

    val fieldWidth = rectWidth * 4 / 5
    val fieldHeight = rectHeight

    fun poly(x1: Int, y11: Int, y12: Int, x2: Int, y21: Int, y22: Int, style: String) {
        context.beginPath()
        context.lineWidth = 2; // In pixels.
        context.setter("strokeStyle", style)
        context.setter("fillStyle", style)

        context.moveTo(x1, fieldHeight - y11)
        context.lineTo(x1, fieldHeight - y12)
        context.lineTo(x2, fieldHeight - y22)
        context.lineTo(x2, fieldHeight - y21)
        context.lineTo(x1, fieldHeight - y11)

        context.fill()

        context.closePath()
        context.stroke()
    }

    fun showValue(value: Int, y: Int, color: String) {
        context.fillStyle = color;
        context.setter("font", "30px Verdana")
        //TODO : Right align the numbers.
        //context.fillText("$value", fieldWidth + 10,  y, rectWidth - fieldWidth - 20) 
        context.fillText("$value", fieldWidth + 10,  rectHeight - y - 10, rectWidth) 
    }

    fun scaleX(x: Int): Int {
        return x * fieldWidth / (Model.backLogSize - 2) 
    }

    fun scaleY(y: Float): Int {
        return (y * fieldHeight).toInt()
    }

    fun clean() {
        context.fillStyle = "#222222"
        context.fillRect(0, 0, rectWidth, rectHeight)
    }

    fun render() {
        clean()
        // we take one less, so that there is no jump from the last to zeroth.
        for (t in 0 until Model.backLogSize-2) {
            val index = (Model.current + t) % (Model.backLogSize - 1)

            val oldTotal = Model.tuple(index).sum()
            val newTotal = Model.tuple(index + 1).sum()

            if (oldTotal == 0 || newTotal == 0) continue // so that we don't divide by zero

            var oldHeight = 0;
            var newHeight = 0;

            for (i in 0 until Model.tupleSize) {
                val style = Model.styles[i]

                val oldValue = Model.colors(index, i)
                val newValue = Model.colors(index+1, i)

                val x1 = scaleX(t)
                val x2 = scaleX(t+1)

                val y11 = scaleY(oldHeight.toFloat() / oldTotal.toFloat())
                val y21 = scaleY(newHeight.toFloat() / newTotal.toFloat())

                val y12 = scaleY((oldHeight + oldValue).toFloat() / oldTotal.toFloat())
                val y22 = scaleY((newHeight + newValue).toFloat() / newTotal.toFloat())

                poly(x1, y11, y12, x2, y21, y22, style);
                
                oldHeight += oldValue
                newHeight += newValue
            }
        }
        for (i in 0 until Model.tupleSize) {
            val style = Model.styles[i]
            val value = Model.colors((Model.current + Model.backLogSize - 1) % Model.backLogSize, i)

            val y = scaleY(i.toFloat() / (Model.tupleSize).toFloat())
            showValue(value, y, style)
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
        val colors = JsArray(json.getProperty("colors"))
        assert(colors.size == Model.tupleSize)

        val tuple = arrayOf<Int>(0, 0, 0, 0, 0)
        for (i in 0 until colors.size) {
            val color = colors[i].getInt("color")
            val counter = colors[i].getInt("counter")
            // Colors are numbered 1..5
            tuple[color-1] = counter
        }
        Model.push(tuple)
    } .then {
        View(canvas).render()
    }
}

fun main(args: Array<String>) {
    html5.setInterval(100) {
        loop()
    }
}

