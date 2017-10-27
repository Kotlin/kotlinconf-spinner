import html5.minimal.*
import kotlinx.wasm.jsinterop.*
import konan.internal.ExportForCppRuntime

object Style {
    val backgroundColor = "#16103f"
    val teamNumberColor = "#38335b"
    val fontColor = "#ffffff"
    val styles = arrayOf("#ff7616", "#f72e2e", "#7a6aea", "#4bb8f6", "#ffffff")
}

object Model {
    val tupleSize = 5
    val styles = Style.styles

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

object Layout {
    val lowerAxisLegend = 0.1
    val fieldHeight = 1.0 - lowerAxisLegend

    val teamNumber = 0.10
    val result = 0.20
    val fieldWidth = 1.0 - teamNumber - result

    val teamBackground = 0.05

    val legendPad = 50
    val teamPad = 50
    val resultPad = 50

    val teamRect = 50
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

    val fieldWidth: Int = (rectWidth.toFloat() * Layout.fieldWidth).toInt()
    val fieldHeight: Int = (rectHeight.toFloat() * Layout.fieldHeight).toInt()

    val teamWidth = (rectWidth.toFloat() * Layout.teamNumber).toInt()
    val teamOffsetX = fieldWidth
    val teamHeight = fieldHeight

    val resultWidth = (rectWidth.toFloat() * Layout.result).toInt()
    val resultOffsetX = fieldWidth + teamWidth
    val resultHeight = fieldHeight

    val legendWidth = fieldWidth
    val legendHeight = (rectWidth.toFloat() * Layout.lowerAxisLegend)
    val legendOffsetY = fieldHeight

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

    fun showValue(index: Int, value: Int, y: Int, color: String) {

        val textCellHeight = teamHeight / Model.tupleSize
        val textBaseline = index * textCellHeight + textCellHeight / 2

        // The team number rectangle.
        context.fillStyle = Style.teamNumberColor
        context.fillRect(teamOffsetX + Layout.teamPad,  teamHeight - textBaseline - Layout.teamRect/2, Layout.teamRect, Layout.teamRect) 

        // The team number in the rectangle.
        context.setter("font", "20px monospace")
        context.setter("textAlign", "center")
        context.setter("textBaseline", "middle")
        context.fillStyle = Style.fontColor
        context.fillText("${index + 1}", teamOffsetX + Layout.teamPad + Layout.teamRect/2,  teamHeight - textBaseline, teamWidth) 

        // The score.
        context.setter("textAlign", "right")
        context.fillStyle = Style.fontColor
        context.fillText("$value", resultOffsetX + Layout.resultPad,  resultHeight - textBaseline,  resultWidth) 

/*
        context.beginPath()
        context.setter("strokeStyle", color)
        context.moveTo(fieldWidth, teamHeight - y)
        context.lineTo(teamOffsetX + Layout.teamPad, teamHeight - textBaseline - Layout.teamRect/2)
        context.stroke()
*/
    }

    fun showLegend() {
        context.setter("font", "20px monospace")
        context.setter("textAlign", "left")
        context.setter("textBaseline", "top")
        context.fillStyle = Style.fontColor
        
        context.fillText("-10 sec", Layout.legendPad, legendOffsetY + Layout.legendPad, legendWidth) 
        context.setter("textAlign", "right")
        context.fillText("now", legendWidth - Layout.legendPad, legendOffsetY + Layout.legendPad, legendWidth) 
    }

    fun scaleX(x: Int): Int {
        return x * fieldWidth / (Model.backLogSize - 2) 
    }

    fun scaleY(y: Float): Int {
        return (y * fieldHeight).toInt()
    }

    fun clean() {
        context.fillStyle = Style.backgroundColor
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
            showValue(i, value, y, style)
        }

        showLegend()
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

