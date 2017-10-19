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

private class TexturedRectRenderer {
    private val program = object : GlShaderProgram(
            vertexShaderSource = """
                #version 300 es

                precision mediump float;

                in vec2 position;
                in vec2 texcoord;
                out vec2 fragTexcoord;

                void main()
                {
                    gl_Position = vec4(position, -0.999, 1.0);

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
    fun render(x: Float, y: Float, w: Float, h: Float, texture: GLuint) {
        this.program.let {
            it.activate()

            val positions = listOf(
                    Vector2(x, y), Vector2(x + w, y), Vector2(x, y + h),
                    Vector2(x + w, y), Vector2(x + w, y + h), Vector2(x, y + h)
            )
            val texCoords = listOf(
                    Vector2(0.0f, 0.0f), Vector2(1.0f, 0.0f), Vector2(0.0f, 1.0f),
                    Vector2(1.0f, 0.0f), Vector2(1.0f, 1.0f), Vector2(0.0f, 1.0f)
            )
            it.position.assign(positions)
            it.texcoord.assign(texCoords)
            it.tex.assign(texture)

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

private class KotlinLogoRenderer(val texture: GLuint) {
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
                    brightness = 0.15 + 0.85 * clamp(brightness, 0.0, 1.0);

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

        this.program.let {
            it.activate()
            it.projection.assign(projection)
            it.modelview.assign(modelview)
            it.position.assign(positions)
            it.normal.assign(normals)
            it.texcoord.assign(texCoords)
            it.tex.assign(texture)

            glDrawElements(
                    GL_TRIANGLES,
                    triangles.size,
                    GL_UNSIGNED_BYTE,
                    triangles.toByteArray().refTo(0)
            )
        }
    }
}

private fun loadTextureFromBmpResource(resourceName: String, textureId: GLenum, texture: GLuint) {
    glActiveTexture(textureId)
    glBindTexture(GL_TEXTURE_2D, texture)
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

private fun TexturedRectRenderer.renderScore(x: Float, y: Float, w: Float, h: Float, score: Int, digitAspect: Float,
                                             labelTexture: Int, labelWidth: Float, labelHeight: Float, labelShift: Float) {
    val margin = 0.01f
    val labelMargin = if (labelTexture >= 0) 0.02f else 0.0f
    val digitWidth = (w - labelWidth - labelMargin - 4 * margin) / 5
    val digits = mutableListOf<Int>()
    var s = score
    while (s > 0) {
        digits += s % 10
        s /= 10
    }
    if (digits.size == 0)
        digits += 0
    digits.reverse()
    var cx = maxOf(0.0f, w - labelWidth - digits.size * digitWidth - (digits.size - 1) * margin) * 0.5f
    val digitHeight = minOf(h, digitWidth / digitAspect)
    digits.forEachIndexed { i, d ->
        render(
                x + cx, y + margin,
                digitWidth, digitHeight - margin,
                d
        )
        cx += digitWidth + margin
    }
    if (labelTexture >= 0) {
        cx += labelMargin - margin
        render(x + cx, y + digitHeight - labelHeight + labelShift,
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
    fun render(x: Float, y: Float, w: Float, h: Float, stats: Stats?, digitAspect: Float, screenAspect: Float) {
        if (stats == null) return

        val barsCount = Team.count
        val marginsCount = barsCount + 1
        val marginToBar = 0.6f
        val highlightedMarginToBar = 0.9f

        val barWidth = w / (barsCount + marginToBar * (marginsCount - 2) + highlightedMarginToBar * 2)
        val marginWidth = barWidth * marginToBar
        val highlightedMarginWidth = barWidth * highlightedMarginToBar

        var maxCount = Team.values().map { stats.getCount(it) }.max() ?: 0
        if (maxCount == 0) maxCount = 1

        val zh = h * 0.4f
        val maxBarH = h * 0.4f
        var barX = x + (if (stats.myTeam.ordinal == 0) highlightedMarginWidth else marginWidth)

        for (team in Team.values()) {
            val barH = 0.01f + (stats.getCount(team).toFloat() / maxCount * maxBarH)
            rectRenderer.render(
                    barX,
                    y + zh,
                    barWidth,
                    barH,
                    team.colorVector
            )
            texturedRectRenderer.renderScore(
                    barX,
                    y + zh + barH,
                    barWidth,
                    h / 8 - 0.01f,
                    stats.getCount(team),
                    digitAspect,
                    -1, 0.0f, 0.0f, 0.0f
            )
            val teamSquareSize = if (team == stats.myTeam) barWidth * 1.3f else barWidth
            val centerY = (zh - barWidth / screenAspect) * 2 / 3 + (barWidth / screenAspect) / 2
            rectRenderer.render(
                    barX + (barWidth - teamSquareSize) / 2,
                    y + centerY - (teamSquareSize / screenAspect / 2),
                    teamSquareSize,
                    teamSquareSize / screenAspect,
                    teamNumberColor
            )
            var digitSize = if (team == stats.myTeam) 0.4f else 0.3f
            texturedRectRenderer.render(
                    barX + (barWidth - teamSquareSize) / 2 + teamSquareSize * (1 - digitSize) / 2,
                    y + centerY - (teamSquareSize / screenAspect / 2) + (teamSquareSize / screenAspect * (1 - digitSize) / 2),
                    teamSquareSize * digitSize,
                    teamSquareSize / screenAspect * digitSize,
                    team.ordinal + 1
            )
            val curMarginWidth = if (team == stats.myTeam || team.ordinal + 1 == stats.myTeam.ordinal) highlightedMarginWidth else marginWidth
            barX += barWidth + curMarginWidth
        }
    }
}

const val kotlinTextureId = 10
const val startScreenTextureId = 11
const val loserScreenTextureId = 12
const val winnerScreenTextureId = 13
const val spinsTextureId = 14
const val numberOfTextures = 15

class GameRenderer {
    private val kotlinLogoRenderer = KotlinLogoRenderer(kotlinTextureId)
    private val statsBarChartRenderer = StatsBarChartRenderer()

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
            val textures = allocArray<GLuintVar>(numberOfTextures)
            glGenTextures(numberOfTextures, textures)
            loadTextureFromBmpResource("mesh_pattern.bmp", GL_TEXTURE10, textures[kotlinTextureId])
            loadTextureFromBmpResource("spinner_game_text.bmp", GL_TEXTURE11, textures[startScreenTextureId])
            loadTextureFromBmpResource("0.bmp", GL_TEXTURE12, textures[loserScreenTextureId])
            loadTextureFromBmpResource("1.bmp", GL_TEXTURE13, textures[winnerScreenTextureId])
            loadTextureFromBmpResource("spins.bmp", GL_TEXTURE14, textures[spinsTextureId])
            for (d in 0..9)
                loadTextureFromBmpResource("$d.bmp", GL_TEXTURE0 + d, textures[d])
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

        if (sceneState.initialized && stats?.status == 0) {
            statsBarChartRenderer.texturedRectRenderer.render(
                    -1.0f, -1.0f,
                    2.0f, 2.0f,
                    if (stats.winner) winnerScreenTextureId else loserScreenTextureId
            )
        } else {

            val squareSize = minOf(screenWidth, screenHeight)
            val digitAspect = 0.375f * screenAspect

            // Project (0, 0, 0) to the center of the screen and
            // make `squareSize / 2` on the screen correspond to `1` in the world,
            // and then move the screen center to the square center:
            val projectionMatrix =
                    translationMatrix(
                            squareSize / screenWidth - 1,
                            1 - squareSize / screenHeight,
                            0.0f
                    ) * orthographicProjectionMatrix(
                            -screenWidth / squareSize, screenWidth / squareSize,
                            -screenHeight / squareSize, screenHeight / squareSize,
                            0.1f, 100.0f
                    )

            kotlinLogoRenderer.render(
                    translationMatrix(0.0f, 0.0f, -2.0f) * Matrix4(sceneState.rotationMatrix),
                    projectionMatrix
            )

            if (sceneState.initialized) {

                if (stats != null) {
                    val width = 2 * squareSize / screenWidth

                    val scoreH = 0.1f
                    val spinsH = scoreH * 0.5f
                    val spinsW = spinsH * (87.0f / 36.0f) * screenAspect
                    statsBarChartRenderer.texturedRectRenderer.renderScore(
                            -(width / 8), 0.85f,
                            width / 4, scoreH,
                            stats.myContribution,
                            digitAspect,
                            spinsTextureId, spinsW, spinsH, 0.0160f
                    )
                }

                if (screenWidth <= screenHeight) {
                    // Portrait orientation. Draw chart below the square:
                    statsBarChartRenderer.render(
                            -1.0f, -1.0f,
                            2.0f, 2 * (screenHeight - squareSize) / screenHeight,
                            stats,
                            digitAspect,
                            screenAspect
                    )
                } else {
                    // Landscape orientation. Draw chart to the right of the square:
                    val width = 2 * (screenWidth - squareSize) / screenWidth
                    statsBarChartRenderer.render(
                            1.0f - width, -1.0f,
                            width, 2.0f,
                            stats,
                            digitAspect,
                            screenAspect
                    )
                }
            } else {
                val ratio = 236.0f / 816.0f
                //  Draw text at the bottom of the square:
                if (screenWidth <= screenHeight) {
                    // Portrait orientation.
                    val height = 2.0f * ratio / screenAspect
                    val y = 1.0f - 2.2f * squareSize / screenHeight
                    statsBarChartRenderer.texturedRectRenderer.render(
                            -1.0f, y,
                            2.0f, height,
                            11
                    )
                } else {
                    // Landscape orientation.
                    val width = 2 * squareSize / screenWidth
                    statsBarChartRenderer.texturedRectRenderer.render(
                            -1.0f, -1.0f,
                            width, width * ratio / screenAspect,
                            11
                    )
                }
            }
        }
    }
}
