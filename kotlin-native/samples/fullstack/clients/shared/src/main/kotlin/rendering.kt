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

import objc.*
import kotlinx.cinterop.*

private class RectRenderer {
    private val program = object : GlShaderProgram(
            vertexShaderSource = """
                #version 300 core

                precision mediump float;

                in vec2 position;

                void main()
                {
                    // Setting z to 0.999 in the clip space places the point behind (almost) everything.
                    gl_Position = vec4(position, 0.999, 1.0);
                }
            """.trimIndent(),

            fragmentShaderSource = """
                #version 300 core

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

private class KotlinLogo(val s: Float, val d: Float) {

    class Face(val indices: IntArray, val texCoords: Array<Vector2>)

    val vertices = arrayOf(
            Vector3(-s, s, -d), Vector3(s, s, -d), Vector3(0.0f, 0.0f, -d), Vector3(s, -s, -d), Vector3(-s, -s, -d),
            Vector3(-s, s, +d), Vector3(s, s, +d), Vector3(0.0f, 0.0f, +d), Vector3(s, -s, +d), Vector3(-s, -s, +d)
    )

    val p = 1.0f / 6.0f

    val faces = arrayOf(
            Face(intArrayOf(0, 1, 2, 3, 4), arrayOf(
                    Vector2(p, 1.0f - p), Vector2(1.0f - p, 1.0f - p), Vector2(0.5f, 0.5f),
                    Vector2(1.0f - p, p), Vector2(p, p))
            ),
            Face(intArrayOf(5, 9, 8, 7, 6), arrayOf(
                    Vector2(p, 1.0f - p), Vector2(p, p), Vector2(1.0f - p, p),
                    Vector2(0.5f, 0.5f), Vector2(1.0f - p, 1.0f - p))
            ),
            Face(intArrayOf(0, 5, 6, 1), arrayOf(
                    Vector2(p, 1.0f - p), Vector2(p, 1.0f),
                    Vector2(1.0f - p, 1.0f), Vector2(1.0f - p, 1.0f - p))
            ),
            Face(intArrayOf(1, 6, 7, 2), arrayOf(
                    Vector2(1.0f - p, 1.0f - p), Vector2(1.0f - 2 * p, 1.0f),
                    Vector2(0.5f - p, 0.5f + p), Vector2(0.5f, 0.5f)
            )),
            Face(intArrayOf(7, 8, 3, 2), arrayOf(
                    Vector2(0.5f - p, 0.5f - p), Vector2(1.0f - 2 * p, 0.0f),
                    Vector2(1.0f - p, p), Vector2(0.5f, 0.5f)
            )),
            Face(intArrayOf(3, 8, 9, 4), arrayOf(
                    Vector2(1.0f - p, p), Vector2(1.0f - p, 0.0f), Vector2(p, 0.0f), Vector2(p, p)
            )),
            Face(intArrayOf(0, 4, 9, 5), arrayOf(
                    Vector2(p, 1.0f - p), Vector2(p, p), Vector2(0.0f, p), Vector2(0.0f, 1.0f - p)
            ))
    )
}

private class KotlinLogoRenderer {
    private val program = object : GlShaderProgram(
            vertexShaderSource = """
                #version 300 core

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
                #version 300 core

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
                    brightness = 0.05 + 0.95 * clamp(brightness, 0.0, 1.0);

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
    }

    private val poly = KotlinLogo(0.5f, 0.167f)

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

            glDrawElements(
                    GL_TRIANGLES,
                    triangles.size,
                    GL_UNSIGNED_BYTE,
                    triangles.toByteArray().refTo(0)
            )
        }
    }

    init {
        loadTextureFromBmpResource("rsz_3kotlin_logo_3d.bmp")
    }
}

private fun loadTextureFromBmpResource(resourceName: String) {
    val texture = memScoped {
        val resultVar = alloc<GLuintVar>()
        glGenTextures(1, resultVar.ptr)
        resultVar.value
    }
    glBindTexture(GL_TEXTURE_2D, texture)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

    val fileBytes = kommon.readResource(resourceName)

    fileBytes.usePinned {
        val fileBytesPtr = it.addressOf(0)
        with(BMPHeader(fileBytesPtr.rawValue)) {
            if (magic != 0x4d42 || zero != 0 || size != fileBytes.size || bits != 24) {
                throw Error("Error parsing texture file")
            }
            val numberOfBytes = width * height * 3
            for (i in 0 until numberOfBytes step 3) {
                val t = data[i]
                data[i] = data[i + 2]
                data[i + 2] = t
            }
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, data)
        }
    }
}

private class StatsBarChartRenderer {
    val rectRenderer = RectRenderer()

    /**
     * Renders a stats bar chart inside the specified rectangle.
     * The coordinate system are the same as in [RectRenderer.render].
     *
     * It makes a padding around the chart.
     */
    fun render(x: Float, y: Float, w: Float, h: Float, stats: Stats?) {
        if (stats == null) return

        val barsCount = Team.count
        val marginsCount = barsCount + 1
        val marginToBar = 0.75f

        val barWidth = w / (barsCount + marginToBar * marginsCount)
        val marginWidth = barWidth * marginToBar

        var maxCount = Team.values().map { stats.getCount(it) }.max() ?: 0
        if (maxCount == 0) maxCount = 1

        for (team in Team.values()) {
            rectRenderer.render(
                    x + (barWidth + marginWidth) * team.ordinal + marginWidth,
                    y + h / 4,
                    barWidth,
                    0.01f + (stats.getCount(team).toFloat() / maxCount * h / 2),
                    team.colorVector
            )
        }
    }
}

class GameRenderer {
    private val kotlinLogoRenderer = KotlinLogoRenderer()
    private val statsBarChartRenderer = StatsBarChartRenderer()

    init {
        glEnable(GL_DEPTH_TEST)
        glDisable(GL_DITHER)
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        glClearDepthf(1.0f)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glEnable(GL_DEPTH_TEST)
    }

    /**
     * Renders the entire game scene.
     *
     * @param screenWidth physical width of the screen in any units
     * @param screenHeight physical height of the screen in the same units as [screenWidth]
     */
    fun render(sceneState: SceneState, screenWidth: Float, screenHeight: Float) {
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Note: all rectangles being drawn below are specified in normalized device coordinates,
        // i.e. the bottom-left corner of the screen is `(-1, -1)` and the top-right is `(1, 1)`.

        val stats = sceneState.stats

        // 1. Draw a square of maximal size in a top-left corner.

        val squareSize = minOf(screenWidth, screenHeight)

        if (stats != null) {
            statsBarChartRenderer.rectRenderer.render(
                    -1.0f, 1.0f - 2 * squareSize / screenHeight,
                    2 * squareSize / screenWidth, 2 * squareSize / screenHeight,
                    stats.myTeam.colorVector
            )
        }

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

        if (screenWidth <= screenHeight) {
            // Portrait orientation. Draw chart below the square:
            statsBarChartRenderer.render(
                    -1.0f, -1.0f,
                    2.0f, 2 * (screenHeight - squareSize) / screenHeight,
                    stats
            )
        } else {
            // Landscape orientation. Draw chart to the right of the square:
            val width = 2 * (screenWidth - squareSize) / screenWidth
            statsBarChartRenderer.render(
                    1.0f - width, -1.0f,
                    width, 2.0f,
                    stats
            )
        }

        kotlinLogoRenderer.render(
                translationMatrix(0.0f, 0.0f, -2.0f) * Matrix4(sceneState.rotationMatrix),
                projectionMatrix
        )


    }
}
