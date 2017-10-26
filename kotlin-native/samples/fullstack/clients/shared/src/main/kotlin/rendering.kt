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
import platform.glescommon.*
import platform.gles3.*
import kommon.*

const val kotlinTextureId = 10
const val startScreenTextureId = 11
const val spinsTextureId = 12
const val konanTextureId = 13
const val gameOverTextureId = 14
const val teamPlaceTextureId = 15
const val totalSpinsTextureId = 16
const val youContributedTextureId = 17
const val winnerTextureId = 18
const val numberOfTextures = 19

val textures = Array<GLuint>(numberOfTextures) { 0 }

private class RectRenderer {
    private val program = object : GlShaderProgram(
            vertexShaderSource = """
                #version 300 es

                precision mediump float;

                in vec2 position;

                void main()
                {
                    // Setting z to 0.999 in the clip space places the point behind (almost) everything.
                    gl_Position = vec4(position, 0.999, 1.0);
                }
            """.trimIndent(),

            fragmentShaderSource = """
                #version 300 es

                precision mediump float;

                uniform vec3 color;

                out vec4 outColor;

                void main()
                {
                    outColor = vec4(color, 1.0);
                }
            """.trimIndent()
    ) {
        val position = Vector2Attribute("position")
        val color = Vector3Uniform("color")
    }

    /**
     * Draws a 2D rectangle specified in a normalized device coordinates,
     * i.e. the bottom-left corner of the screen is `(-1, -1)` and the top-right is `(1, 1)`.
     */
    fun render(x: Float, y: Float, w: Float, h: Float, color: Vector3) {
        this.program.let {
            it.activate()

            it.color.assign(color)
            val positions = listOf(
                    Vector2(x, y), Vector2(x + w, y), Vector2(x, y + h),
                    Vector2(x + w, y), Vector2(x + w, y + h), Vector2(x, y + h)
            )
            it.position.assign(positions)

            glDrawArrays(GL_TRIANGLES, 0, positions.size)
        }
    }
}

private class TexturedRectRenderer(val z: Double = -0.99) {
    private val program = object : GlShaderProgram(
            vertexShaderSource = """
                #version 300 es

                precision mediump float;

                in vec2 position;
                in vec2 texcoord;
                out vec2 fragTexcoord;

                void main()
                {
                    gl_Position = vec4(position, $z, 1.0);

                    fragTexcoord = texcoord;
                }
            """.trimIndent(),

            fragmentShaderSource = """
                #version 300 es

                precision mediump float;

                uniform sampler2D tex;

                in vec2 fragTexcoord;
                out vec4 outColor;

                void main()
                {
                    outColor = texture(tex, fragTexcoord);
                }
            """.trimIndent()
    ) {
        val position = Vector2Attribute("position")
        val texcoord = Vector2Attribute("texcoord")
        val tex = IntUniform("tex")
    }

    /**
     * Draws a 2D rectangle specified in a normalized device coordinates,
     * i.e. the bottom-left corner of the screen is `(-1, -1)` and the top-right is `(1, 1)`.
     */
    fun render(x: Float, y: Float, w: Float, h: Float, texture: Int,
               texBottomLeft: Vector2 = Vector2(0.0f, 0.0f),
               texUpperRight: Vector2 = Vector2(1.0f, 1.0f)) {
        glBindTexture(GL_TEXTURE_2D, textures[texture])
        this.program.let {
            it.activate()

            val positions = listOf(
                    Vector2(x, y), Vector2(x + w, y), Vector2(x, y + h),
                    Vector2(x + w, y), Vector2(x + w, y + h), Vector2(x, y + h)
            )
            val texCoords = listOf(
                    texBottomLeft, Vector2(texUpperRight.x, texBottomLeft.y), Vector2(texBottomLeft.x, texUpperRight.y),
                    Vector2(texUpperRight.x, texBottomLeft.y), texUpperRight, Vector2(texBottomLeft.x, texUpperRight.y)
            )
            it.position.assign(positions)
            it.texcoord.assign(texCoords)
            it.tex.assign(0)

            glDrawArrays(GL_TRIANGLES, 0, positions.size)
        }
    }
}

private class KotlinLogo(val s: Float, val d: Float) {

    class Face(val indices: IntArray, val texCoords: Array<Vector2>)

    val vertices = arrayOf(
            Vector3(-s, s, -d), Vector3(s, s, -d), Vector3(0.0f, 0.0f, -d), Vector3(s, -s, -d), Vector3(-s, -s, -d),
            Vector3(-s, s, +d), Vector3(s, s, +d), Vector3(0.0f, 0.0f, +d), Vector3(s, -s, +d), Vector3(-s, -s, +d)
    )

    val faces = arrayOf(
            Face(intArrayOf(0, 1, 2, 3, 4), arrayOf(
                    Vector2(0.0f, 0.0f), Vector2(0.0f, 1.0f), Vector2(0.5f, 0.5f),
                    Vector2(1.0f, 1.0f), Vector2(1.0f, 0.0f))
            ),
            Face(intArrayOf(5, 9, 8, 7, 6), arrayOf(
                    Vector2(1.0f, 0.0f), Vector2(0.0f, 0.0f), Vector2(0.0f, 1.0f),
                    Vector2(0.5f, 0.5f), Vector2(1.0f, 1.0f))
            ),
            Face(intArrayOf(0, 5, 6, 1), arrayOf(
                    Vector2(0.0f, 0.0f), Vector2(0.0f, 1.0f),
                    Vector2(1.0f, 1.0f), Vector2(1.0f, 0.0f))
            ),
            Face(intArrayOf(1, 6, 7, 2), arrayOf(
                    Vector2(0.0f, 0.0f), Vector2(0.0f, 1.0f),
                    Vector2(0.5f, 1.0f), Vector2(0.5f, 0.0f)
            )),
            Face(intArrayOf(7, 8, 3, 2), arrayOf(
                    Vector2(0.0f, 0.0f), Vector2(0.5f, 0.0f),
                    Vector2(0.5f, 1.0f), Vector2(0.0f, 1.0f)
            )),
            Face(intArrayOf(3, 8, 9, 4), arrayOf(
                    Vector2(0.0f, 0.0f), Vector2(1.0f, 0.0f),
                    Vector2(1.0f, 1.0f), Vector2(0.0f, 1.0f)
            )),
            Face(intArrayOf(0, 4, 9, 5), arrayOf(
                    Vector2(0.0f, 0.0f), Vector2(0.0f, 1.0f),
                    Vector2(1.0f, 1.0f), Vector2(1.0f, 0.0f)
            ))
    )
}

private class KotlinLogoRenderer(val texture: Int) {
    private val program = object : GlShaderProgram(
            vertexShaderSource = """
                #version 300 es

                precision mediump float;

                uniform mat4 projection;
                uniform mat4 modelview;

                in vec3 position;
                in vec3 normal;
                in vec2 texcoord;

                out vec4 fragPosition;
                out vec3 fragNormal;
                out vec2 fragTexcoord;

                void main()
                {
                    vec4 position4 = vec4(position, 1);
                    gl_Position = projection * modelview * position4;

                    fragPosition = position4;
                    fragTexcoord = texcoord;
                    fragNormal = normal;
                }
            """.trimIndent(),

            fragmentShaderSource = """
                #version 300 es

                precision mediump float;

                uniform sampler2D tex;
                uniform mat4 modelview;

                in vec2 fragTexcoord;
                in vec3 fragNormal;
                in vec4 fragPosition;

                out vec4 outColor;

                void main()
                {
                    mat3 normalMatrix = transpose(inverse(mat3(modelview)));
                    // The normal in eye-space coordinates:
                    vec3 normal = normalize(normalMatrix * fragNormal);

                    // The trivial hard-coded ambient-diffuse light:
                    vec3 surfaceToLight = vec3(0, 0, 2) - vec3(modelview * fragPosition);
                    float brightness = dot(normal, surfaceToLight) / (length(surfaceToLight) * length(normal));
                    //brightness = 0.05 + 0.95 * clamp(brightness, 0.0, 1.0);
                    brightness = 0.50 + 0.50 * clamp(brightness, 0.0, 1.0);

                    vec4 textureColor = texture(tex, fragTexcoord);
                    outColor = vec4(brightness * textureColor.rgb, textureColor.a);
                }
            """.trimIndent()
    ) {
        val projection = Matrix4Uniform("projection")
        val modelview = Matrix4Uniform("modelview")
        val position = Vector3Attribute("position")
        val normal = Vector3Attribute("normal")
        val texcoord = Vector2Attribute("texcoord")
        val tex = IntUniform("tex")
    }

    private val poly = KotlinLogo(0.5f, 0.5f)

    fun render(modelview: Matrix4, projection: Matrix4) {

        val positions = mutableListOf<Vector3>()
        val triangles = mutableListOf<Byte>()
        val normals = mutableListOf<Vector3>()
        val texCoords = mutableListOf<Vector2>()

        for (f in poly.faces.indices) {
            val face = poly.faces[f]
            val u = poly.vertices[face.indices[2]] - poly.vertices[face.indices[1]]
            val v = poly.vertices[face.indices[0]] - poly.vertices[face.indices[1]]
            var normal = u.crossProduct(v).normalized()


            val copiedFace = ByteArray(face.indices.size)
            for (j in face.indices.indices) {
                copiedFace[j] = positions.size.toByte()
                positions.add(poly.vertices[face.indices[j]])
                normals.add(normal)
                texCoords.add(face.texCoords[j])
            }

            for (j in 1..face.indices.size - 2) {
                triangles.add(copiedFace[0])
                triangles.add(copiedFace[j])
                triangles.add(copiedFace[j + 1])
            }
        }

        glBindTexture(GL_TEXTURE_2D, textures[texture])

        this.program.let {
            it.activate()
            it.projection.assign(projection)
            it.modelview.assign(modelview)
            it.position.assign(positions)
            it.normal.assign(normals)
            it.texcoord.assign(texCoords)
            it.tex.assign(0)

            glDrawElements(
                    GL_TRIANGLES,
                    triangles.size,
                    GL_UNSIGNED_BYTE,
                    triangles.toByteArray().refTo(0)
            )
        }
    }
}

private fun loadTextureFromBmpResource(resourceName: String, texture: Int) {
    glBindTexture(GL_TEXTURE_2D, textures[texture])
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

    val fileBytes = kommon.readResource(resourceName)

    fileBytes.usePinned {
        val fileBytesPtr = it.addressOf(0)
        with(BMPHeader(fileBytesPtr.rawValue)) {
            if (magic.toInt() != 0x4d42 || fileSize != fileBytes.size) {
                throw Error("Error parsing texture file")
            }
            when (bits.toInt()) {
                24 -> {
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, data)
                }
                32 -> {
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data)
                }
                else -> error("Unsupported bmp format")
            }
        }
    }
    checkGlError()
}

private fun TexturedRectRenderer.renderScore(x: Float, y: Float, w: Float, score: Int,
                                             maxDigits: Float, digitAspect: Float, margin: Float,
                                             labelPlace: Int, labelTexture: Int, labelAspect: Float,
                                             labelScale: Float, labelShift: Float, labelMargin: Float) {
    val digitWidth = (w - labelMargin - (maxDigits - 1) * margin) / (maxDigits + labelAspect * labelScale / digitAspect)
    val digits = mutableListOf<Int>()
    var s = score
    while (s > 0) {
        digits += s % 10
        s /= 10
    }
    if (digits.size == 0)
        digits += 0
    digits.reverse()
    val digitHeight = digitWidth / digitAspect
    val labelHeight = digitHeight * labelScale
    val labelWidth  = labelHeight * labelAspect
    var cx = (w - labelWidth - labelMargin - digits.size * digitWidth - (digits.size - 1) * margin) * 0.5f
    if (labelPlace < 0) {
        render(x + cx, y + digitHeight - labelHeight + labelShift * labelHeight,
                labelWidth, labelHeight,
                labelTexture
        )
        cx += labelWidth + labelMargin
    }
    digits.forEachIndexed { i, d ->
        render(
                x + cx, y,
                digitWidth, digitHeight,
                d
        )
        cx += digitWidth + margin
    }
    if (labelPlace > 0) {
        cx += labelMargin - margin
        render(x + cx, y + digitHeight - labelHeight + labelShift * labelHeight,
                labelWidth, labelHeight,
                labelTexture
        )
    }
}

private val backgroundColor = intToColorVector(0x16103f)
private val teamNumberColor = intToColorVector(0x38335b)

private class StatsBarChartRenderer {
    val rectRenderer = RectRenderer()
    val texturedRectRenderer = TexturedRectRenderer()

    /**
     * Renders a stats bar chart inside the specified rectangle.
     * The coordinate system are the same as in [RectRenderer.render].
     *
     * It makes a padding around the chart.
     */
    fun render(x: Float, y: Float, w: Float, h: Float, myTeam: Team?, counts: List<Int>, digitAspect: Float, screenAspect: Float) {
        val barsCount = Team.count
        val allMarginsCount = barsCount - 1
        val highlightedMarginsCount = when {
            myTeam == null -> 0
            myTeam.ordinal == 0 || myTeam.ordinal == Team.count - 1 -> 1
            else -> 2
        }
        val marginsCount = allMarginsCount - highlightedMarginsCount
        val marginToBar = 4.53333f / 10.93333f
        val highlightedMarginToBar = 9.06667f / 10.93333f

        val barWidth = w / (barsCount + marginToBar * marginsCount + highlightedMarginToBar * highlightedMarginsCount)
        val marginWidth = barWidth * marginToBar
        val highlightedMarginWidth = barWidth * highlightedMarginToBar

        var maxCount = counts.max() ?: 0
        if (maxCount == 0) maxCount = 1

        val maxBarH = (h - barWidth / screenAspect - 4 * marginWidth / screenAspect)
        val zh = barWidth / screenAspect + 2 * marginWidth / screenAspect
        var barX = x

        for (team in Team.values()) {
            val barH = 0.01f + (counts[team.ordinal].toFloat() / maxCount * maxBarH)
            rectRenderer.render(
                    barX,
                    y + zh,
                    barWidth,
                    barH,
                    team.colorVector
            )
            val teamSquareSize = if (team == myTeam) barWidth * 1.3f else barWidth
            val centerY = (zh - barWidth / screenAspect) * 1 / 2 + (barWidth / screenAspect) / 2
            val dist = zh - (centerY + (teamSquareSize / screenAspect) * 0.5f)
            texturedRectRenderer.renderScore(
                    barX,
                    y + zh + barH + dist * 0.5f,
                    barWidth,
                    counts[team.ordinal],
                    if (team == myTeam) 5.5f * 12 / 16 else 5.5f,
                    digitAspect, 0.01f,
                    0, -1, 0.0f, 0.0f, 0.0f, 0.02f
            )
            rectRenderer.render(
                    barX + (barWidth - teamSquareSize) / 2,
                    y + centerY - (teamSquareSize / screenAspect / 2),
                    teamSquareSize,
                    teamSquareSize / screenAspect,
                    teamNumberColor
            )
            val digitSize = if (team == myTeam) 0.225f else 0.175f
            val digitW = digitSize
            val digitH = digitW / digitAspect
            texturedRectRenderer.render(
                    barX + (barWidth - teamSquareSize) / 2 + teamSquareSize * (1 - digitW) / 2,
                    y + centerY - (teamSquareSize / screenAspect / 2) + ((teamSquareSize / screenAspect - teamSquareSize * digitH) / 2),
                    teamSquareSize * digitW,
                    teamSquareSize * digitH,
                    team.ordinal + 1
            )
            val curMarginWidth = if (team == myTeam || team.ordinal + 1 == myTeam?.ordinal) highlightedMarginWidth else marginWidth
            barX += barWidth + curMarginWidth
        }
    }
}

class GameRenderer {
    private val kotlinLogoRenderer = KotlinLogoRenderer(kotlinTextureId)
    private val statsBarChartRenderer = StatsBarChartRenderer()
    private val placeRenderer = TexturedRectRenderer(-0.999)

    init {
        glEnable(GL_DEPTH_TEST)
        glDisable(GL_DITHER)
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        glClearDepthf(1.0f)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        memScoped {
            val glGenTexturesResult = allocArray<GLuintVar>(numberOfTextures)
            glGenTextures(numberOfTextures, glGenTexturesResult)
            for (i in 0 until numberOfTextures)
                textures[i] = glGenTexturesResult[i]

            loadTextureFromBmpResource("mesh_pattern.bmp", kotlinTextureId)
            loadTextureFromBmpResource("spinner_game_text.bmp", startScreenTextureId)
            loadTextureFromBmpResource("spins.bmp", spinsTextureId)
            loadTextureFromBmpResource("konan.bmp", konanTextureId)
            loadTextureFromBmpResource("game_over.bmp", gameOverTextureId)
            loadTextureFromBmpResource("team_place.bmp", teamPlaceTextureId)
            loadTextureFromBmpResource("total_spins.bmp", totalSpinsTextureId)
            loadTextureFromBmpResource("you_contributed.bmp", youContributedTextureId)
            loadTextureFromBmpResource("winner.bmp", winnerTextureId)
            for (d in 0..9)
                loadTextureFromBmpResource("$d.bmp", d)
        }
    }

    /**
     * Renders the entire game scene.
     *
     * @param screenWidth physical width of the screen in any units
     * @param screenHeight physical height of the screen in the same units as [screenWidth]
     */
    fun render(sceneState: SceneState, screenWidth: Float, screenHeight: Float) {
        glClearColor(backgroundColor.x, backgroundColor.y, backgroundColor.z, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Note: all rectangles being drawn below are specified in normalized device coordinates,
        // i.e. the bottom-left corner of the screen is `(-1, -1)` and the top-right is `(1, 1)`.

        val screenAspect = screenHeight / screenWidth

        val stats = sceneState.stats
        val gameOver = sceneState.initialized && stats?.status == 0
        val showCopyright = stats?.status == 2

        val squareSize = minOf(screenWidth, screenHeight)
        val digitAspect = 48.0f / 76.0f * screenAspect

        // Project (0, 0, 0) to the center of the screen and
        // make `squareSize / 2` on the screen correspond to `1` in the world,
        // and then move the screen center to the square center:
        val projectionMatrix =
                translationMatrix(
                        squareSize / screenWidth - 1,
                        0.0f - (-1.0f + 33.03448f / 100 * 2.0f),
                        0.0f
                ) * orthographicProjectionMatrix(
                        -screenWidth / squareSize, screenWidth / squareSize,
                        -screenHeight / squareSize, screenHeight / squareSize,
                        0.1f, 100.0f
                )

        if (!gameOver) {
            kotlinLogoRenderer.render(
                    translationMatrix(0.0f, 0.0f, -2.0f) * Matrix4(sceneState.rotationMatrix),
                    projectionMatrix
            )
        }

        val myContribution = if (sceneState.initialized && stats != null)
                                 stats.myContribution
                             else 0
        val myTeam = if (sceneState.initialized && stats != null)
                         stats.myTeam
                     else null
        val counts = if (sceneState.initialized && stats != null)
                         Team.values().map { stats.getCount(it) }
                     else IntArray(Team.count, { 0 }).asList()

        fun renderCenteredTexture(y: Float, w: Float, aspectRatio: Float, textureId: Int) {
            statsBarChartRenderer.texturedRectRenderer.render(
                    -1.0f + (2.0f - w) / 2, y,
                    w, w * aspectRatio / screenAspect,
                    textureId
            )
        }

        val margin = 9.06667f / 100 * 2.0f

        if (!gameOver) {
            if (sceneState.initialized) {
                val scoreH = 0.06f
                val spinsAspect = (102.0f / 36.0f) * screenAspect
                val scoreW = scoreH * digitAspect * 7
                statsBarChartRenderer.texturedRectRenderer.renderScore(
                        -1.0f + (2.0f - scoreW) / 2, 1.0f - (8.0f / 100 * 2.0f),
                        scoreW,
                        myContribution,
                        5.5f * 12 / 18,
                        digitAspect, 0.01f,
                        1, spinsTextureId, spinsAspect, 0.6f, 0.32f, 0.02f
                )
            }
        } else {
            if (stats!!.winner) {
                renderCenteredTexture(
                        1.0f - (17.5f / 100 * 2.0f),
                        2.0f - margin * 2,
                        45.0f / 613.0f,
                        winnerTextureId
                )
            } else {
                renderCenteredTexture(
                        1.0f - (15.0f / 100 * 2.0f),
                        2.0f * 0.3f,
                        48.0f / 291.0f,
                        gameOverTextureId
                )
            }
            renderCenteredTexture(
                    1.0f - (27.5f / 100 * 2.0f),
                    2.0f * 0.75f,
                    57.0f / 969.0f,
                    teamPlaceTextureId
            )
            val myCount = counts[myTeam!!.ordinal]
            val place = counts.count { it > myCount } + 1
            val placeH = 0.035f
            val placeW = placeH * digitAspect
            placeRenderer.renderScore(
                    0.165f, 1.0f - (27.0f / 100 * 2.0f),
                    placeW,
                    place,
                    1.0f,
                    digitAspect, 0.01f,
                    0, -1, 0.0f, 0.0f, 0.0f, 0.0f
            )

            val totalH = 0.08f
            val totalW = totalH * digitAspect * 7
            statsBarChartRenderer.texturedRectRenderer.renderScore(
                    -1.0f + (2.0f - totalW) / 2, 1.0f - (37.5f / 100 * 2.0f),
                    totalW,
                    myCount,
                    5.5f * 12 / 20,
                    digitAspect, 0.03f,
                    0, -1, 0.0f, 0.0f, 0.0f, 0.0f
            )
            renderCenteredTexture(
                    1.0f - (42.5f / 100 * 2.0f),
                    2.0f * 0.3f,
                    57.0f / 394.0f,
                    totalSpinsTextureId
            )

            val scoreH = 0.125f
            val youContributedAspect = (309.0f / 33.0f) * screenAspect
            val scoreW = scoreH * digitAspect * 7
            statsBarChartRenderer.texturedRectRenderer.renderScore(
                    -1.0f + (2.0f - scoreW) / 2, 1.0f - (58.5f / 100 * 2.0f),
                    scoreW,
                    myContribution,
                    5.5f * 12 / 11,
                    digitAspect, 0.01f,
                    -1, youContributedTextureId, youContributedAspect, 1.0f, 0.0f, 0.05f
            )
        }

        statsBarChartRenderer.rectRenderer.render(
                (-1.0f + margin),
                (1.0f - 62.0f / 100 * 2.0f),
                (2.0f - margin * 2),
                0.005f,
                Vector3(1.0f, 1.0f, 1.0f)
        )

        if (sceneState.initialized) {
            if (screenWidth <= screenHeight) {
                // Portrait orientation. Draw chart below the square:
                statsBarChartRenderer.render(
                        -1.0f + margin, -1.0f,
                        2.0f - margin * 2, (2.0f - 62.0f / 100 * 2.0f),
                        myTeam,
                        counts,
                        digitAspect,
                        screenAspect
                )
            } else {
                // Landscape orientation. Draw chart to the right of the square:
                val width = 2 * (screenWidth - squareSize) / screenWidth
                statsBarChartRenderer.render(
                        1.0f - width, -1.0f,
                        width, 2.0f,
                        myTeam,
                        counts,
                        digitAspect,
                        screenAspect
                )
            }
        }

        if (!sceneState.initialized) {
            val startMessageRatio = 175.0f / 801.0f
            //  Draw text at the bottom of the square:
            if (screenWidth <= screenHeight) {
                // Portrait orientation.
                renderCenteredTexture(
                        1.0f - 61.0f / 100 * 2.0f,
                        2.0f - margin * 2, startMessageRatio,
                        startScreenTextureId
                )

                if (showCopyright) {
                    renderCenteredTexture(
                            1.0f - 75.0f / 100 * 2.0f,
                            1.0f, 39.0f / 492.0f,
                            konanTextureId
                    )
                }

            }
        }
    }
}
