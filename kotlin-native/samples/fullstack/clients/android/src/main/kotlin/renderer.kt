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
import android.*
import common.utsname
import common.uname
import kurl.*
import kjson.*
import konan.worker.*

fun machineName() =
        memScoped {
            val u = alloc<utsname>()
            if (uname(u.ptr) == 0) {
                "${u.sysname.toKString()} ${u.machine.toKString()}"
            } else {
                "unknown"
            }
        }

class Stats(val nativeActivity: ANativeActivity) {
    private val server = "http://kotlin-demo.kotlinconf.com:8080"
    private val name = "Android user"

    private fun cookiesFileName() = "${nativeActivity.internalDataPath!!.toKString()}/cookies.txt"

    private val worker = startWorker()
    private var future: Future<Any>? = null
    val counts = IntArray(5) { 0 }
    var myColor = 0

    class WorkerArgument(val url: String, val cookiesFileName: String)

    fun initialize() = memScoped {
        val machine = machineName()
        logInfo("Connecting to $server as $name from $machine")
        logInfo("Internal data path = ${nativeActivity.internalDataPath!!.toKString()}")
        val kurl = KUrl(cookiesFileName())
        val url = "$server/json/stats?name=${kurl.escape(name)}&client=android&machine=${kurl.escape(machine)}"
        logInfo(url)
        try {
            withUrl(kurl) {
                it.fetch(url) {
                    withJson(it) {
                        myColor = it.getInt("color") - 1
                        logInfo("Got $it, my color is ${myColor + 1}")
                        val colors = it.getArray("colors")
                        counts.indices.forEach {
                            val obj = colors.getObject(it)
                            val color = obj.getInt("color")
                            val counter = obj.getInt("counter")
                            logInfo("Color: $color, counter = $counter")
                            counts[color - 1] = counter
                        }
                    }
                }
            }
        } catch (error: KUrlError) {
            logError("network problem: $error")
        } finally {
            kurl.close()
        }
    }

    fun tryClick(): Boolean {
        if (future?.state == FutureState.SCHEDULED) return false
        future = worker.schedule(TransferMode.CHECKED, { WorkerArgument("$server/json/click", cookiesFileName()) }) {
            val kurl = KUrl(it.cookiesFileName)
            val url = it.url
            try {
                withUrl(kurl) {
                    var result: Any? = null
                    it.fetch(url) {
                        result = KJsonObject(it)
                    }
                    result!!
                }
            } catch (error: KUrlError) {
                "network problem: $error"
            } catch (t: Throwable) {
                "Esception: $t"
            } finally {
                kurl.close()
            }
        }
        return true
    }

    fun refresh() {
        val future = this.future
        if (future != null && future.state == FutureState.COMPUTED) {
            future.consume {
                if (it is String)
                    logError(it)
                else {
                    if (it !is KJsonObject)
                        logError("A KJsonObject expected but was $it")
                    else {
                        val colors = it.getArray("colors")
                        counts.indices.forEach {
                            val obj = colors.getObject(it)
                            val color = obj.getInt("color")
                            val counter = obj.getInt("counter")
                            logInfo("Color: $color, counter = $counter")
                            counts[color - 1] = counter
                        }
                        it.close()
                    }
                }
            }
        }
    }
}

class Renderer(val parentArena: NativePlacement, val nativeActivity: ANativeActivity, val savedMatrix: COpaquePointer?) {

    private val arena = Arena()
    private var display: EGLDisplay? = null
    private var surface: EGLSurface? = null
    private var context: EGLContext? = null
    private var initialized = false

    var screen = Vector2.Zero
    private val stats = Stats(nativeActivity)

    private val matrix = parentArena.allocArray<FloatVar>(16)

    init {
        if (savedMatrix != null) {
            memcpy(matrix, savedMatrix, 16 * 4)
        } else {
            for (i in 0..3)
                for (j in 0..3)
                    matrix[i * 4 + j] = if (i == j) 1.0f else 0.0f
        }
    }

    fun initialize(window: CPointer<ANativeWindow>): Boolean {
        with(arena) {
            stats.initialize()
            logInfo("Initializing context..")
            display = eglGetDisplay(null)
            if (display == null) {
                logError("eglGetDisplay() returned error ${eglGetError()}")
                return false
            }
            if (eglInitialize(display, null, null) == 0) {
                logError("eglInitialize() returned error ${eglGetError()}")
                return false
            }

            val attribs = cValuesOf(
                    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                    EGL_BLUE_SIZE, 8,
                    EGL_GREEN_SIZE, 8,
                    EGL_RED_SIZE, 8,
                    EGL_DEPTH_SIZE, 24,
                    EGL_NONE
            )
            val numConfigs = alloc<EGLintVar>()
            if (eglChooseConfig(display, attribs, null, 0, numConfigs.ptr) == 0) {
                logError("eglChooseConfig()#1 returned error ${eglGetError()}")
                destroy()
                return false
            }
            val supportedConfigs = allocArray<EGLConfigVar>(numConfigs.value)
            if (eglChooseConfig(display, attribs, supportedConfigs, numConfigs.value, numConfigs.ptr) == 0) {
                logError("eglChooseConfig()#2 returned error ${eglGetError()}")
                destroy()
                return false
            }
            var configIndex = 0
            while (configIndex < numConfigs.value) {
                val r = alloc<EGLintVar>()
                val g = alloc<EGLintVar>()
                val b = alloc<EGLintVar>()
                val d = alloc<EGLintVar>()
                if (eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_RED_SIZE, r.ptr) != 0 &&
                        eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_GREEN_SIZE, g.ptr) != 0 &&
                        eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_BLUE_SIZE, b.ptr) != 0 &&
                        eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_DEPTH_SIZE, d.ptr) != 0 &&
                        r.value == 8 && g.value == 8 && b.value == 8 && d.value >= 24) {
                    break
                }
                ++configIndex
            }
            if (configIndex >= numConfigs.value)
                configIndex = 0

            surface = eglCreateWindowSurface(display, supportedConfigs[configIndex], window, null)
            if (surface == null) {
                logError("eglCreateWindowSurface() returned error ${eglGetError()}")
                destroy()
                return false
            }

            context = eglCreateContext(display, supportedConfigs[configIndex], null, null)
            if (context == null) {
                logError("eglCreateContext() returned error ${eglGetError()}")
                destroy()
                return false
            }

            if (eglMakeCurrent(display, surface, surface, context) == 0) {
                logError("eglMakeCurrent() returned error ${eglGetError()}")
                destroy()
                return false
            }

            val width = alloc<EGLintVar>()
            val height = alloc<EGLintVar>()
            if (eglQuerySurface(display, surface, EGL_WIDTH, width.ptr) == 0
                    || eglQuerySurface(display, surface, EGL_HEIGHT, height.ptr) == 0) {
                logError("eglQuerySurface() returned error ${eglGetError()}")
                destroy()
                return false
            }

            this@Renderer.screen = Vector2(width.value.toFloat(), height.value.toFloat())

            glDisable(GL_DITHER)
            glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_FASTEST)
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            glClearDepthf(1.0f)
            glEnable(GL_CULL_FACE)
            glCullFace(GL_BACK)
            glShadeModel(GL_FLAT)
            glEnable(GL_DEPTH_TEST)

            glViewport(0, 0, width.value, height.value)

            val ratio = width.value.toFloat() / height.value
            glMatrixMode(GL_PROJECTION)
            glLoadIdentity()
            glOrthof(-ratio, ratio, -1.0f, 1.0f, 0.1f, 100.0f)

            glMatrixMode(GL_MODELVIEW)
            glTranslatef(0.0f, 0.0f, -2.0f)
            glRotatef(180.0f, 1.0f, 0.0f, 0.0f)
            glLightfv(GL_LIGHT0, GL_POSITION, cValuesOf(0.0f, 0.0f, 2.0f, 0.0f))
            glEnable(GL_LIGHTING)
            glEnable(GL_LIGHT0)
            glMaterialfv(GL_FRONT_AND_BACK, GL_DIFFUSE, cValuesOf(0.8f, 0.8f, 0.8f, 1.0f))
            glMaterialfv(GL_FRONT_AND_BACK, GL_SPECULAR, cValuesOf(0.1f, 0.1f, 0.1f, 1.0f))
            glMaterialf(GL_FRONT_AND_BACK, GL_SHININESS, 0.0f)

            loadTexture("rsz_3kotlin_logo_3d.bmp")

            initialized = true
            return true
        }
    }

    fun getState() = matrix to 16 * 4

    private var curAngle = 0.0f
    private val threshold = 720.0f

    fun rotateBy(vec: Vector2) {
        if (!initialized) return

        val len = vec.length
        if (len < 1e-9f) return
        val angle = 180 * len / screen.length
        curAngle += angle
        if (curAngle >= threshold && stats.tryClick())
            curAngle -= threshold

        val x = vec.y / len
        val y = -vec.x / len

        glPushMatrix()
        glMatrixMode(GL_MODELVIEW)
        glLoadIdentity()
        glRotatef(angle, x, y, 0.0f)
        glMultMatrixf(matrix)
        glGetFloatv(GL_MODELVIEW_MATRIX, matrix)
        glPopMatrix()
    }

    private class BMPHeader(val rawPtr: NativePtr) {
        inline fun <reified T : CPointed> memberAt(offset: Long): T {
            return interpretPointed<T>(this.rawPtr + offset)
        }

        val magic get() = memberAt<ShortVar>(0).value.toInt()
        val size get() = memberAt<IntVar>(2).value
        val zero get() = memberAt<IntVar>(6).value
        val width get() = memberAt<IntVar>(18).value
        val height get() = memberAt<IntVar>(22).value
        val bits get() = memberAt<ShortVar>(28).value.toInt()
        val data get() = interpretCPointer<ByteVar>(rawPtr + 54) as CArrayPointer<ByteVar>
    }

    private fun loadTexture(assetName: String): Unit = memScoped {
        val asset = AAssetManager_open(nativeActivity.assetManager, assetName, AASSET_MODE_BUFFER)
        if (asset == null) {
            logError("Error opening asset")
            return
        }
        val length = AAsset_getLength(asset)
        val buf = allocArray<ByteVar>(length)
        if (AAsset_read(asset, buf, length) != length.toInt()) {
            logError("Error reading asset")
            AAsset_close(asset)
        }
        with(BMPHeader(buf.rawValue)) {
            if (magic != 0x4d42 || zero != 0 || size != length.toInt() || bits != 24) {
                logError("Error parsing texture file")
                AAsset_close(asset)
                return
            }
            val numberOfBytes = width * height * 3
            for (i in 0 until numberOfBytes step 3) {
                val t = data[i]
                data[i] = data[i + 2]
                data[i + 2] = t
            }
            glBindTexture(GL_TEXTURE_2D, 1)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glEnable(GL_TEXTURE_2D)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, data)
            AAsset_close(asset)
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
                Face(intArrayOf(0, 1, 2, 3, 4), arrayOf(Vector2(p, 1.0f - p), Vector2(1.0f - p, 1.0f - p), Vector2(0.5f, 0.5f), Vector2(1.0f - p, p), Vector2(p, p))),
                Face(intArrayOf(5, 9, 8, 7, 6), arrayOf(Vector2(p, 1.0f - p), Vector2(p, p), Vector2(1.0f - p, p), Vector2(0.5f, 0.5f), Vector2(1.0f - p, 1.0f - p))),
                Face(intArrayOf(0, 5, 6, 1), arrayOf(Vector2(p, 1.0f - p), Vector2(p, 1.0f), Vector2(1.0f - p, 1.0f), Vector2(1.0f - p, 1.0f - p))),
                Face(intArrayOf(1, 6, 7, 2), arrayOf(Vector2(1.0f - p, 1.0f - p), Vector2(1.0f - 2 * p, 1.0f), Vector2(0.5f - p, 0.5f + p), Vector2(0.5f, 0.5f))),
                Face(intArrayOf(7, 8, 3, 2), arrayOf(Vector2(0.5f - p, 0.5f - p), Vector2(1.0f - 2 * p, 0.0f), Vector2(1.0f - p, p), Vector2(0.5f, 0.5f))),
                Face(intArrayOf(3, 8, 9, 4), arrayOf(Vector2(1.0f - p, p), Vector2(1.0f - p, 0.0f), Vector2(p, 0.0f), Vector2(p, p))),
                Face(intArrayOf(0, 4, 9, 5), arrayOf(Vector2(p, 1.0f - p), Vector2(p, p), Vector2(0.0f, p), Vector2(0.0f, 1.0f - p)))
        )
    }

    private fun drawRect(x: Float, y: Float, w: Float, h: Float, z: Float, s: Float, color: Vector3) = memScoped {
        val vertices = mutableListOf<Float>()
        val triangles = mutableListOf<Byte>()
        val normals = mutableListOf<Float>()

        glEnableClientState(GL_VERTEX_ARRAY)
        glEnableClientState(GL_NORMAL_ARRAY)
        glDisableClientState(GL_TEXTURE_COORD_ARRAY)

        vertices += listOf(x, y, z, s)
        vertices += listOf(x, y + h, z, s)
        vertices += listOf(x + w, y + h, z, s)
        if (h >= 0.0f)
            triangles += listOf(0.toByte(), 1.toByte(), 2.toByte())
        else
            triangles += listOf(0.toByte(), 2.toByte(), 1.toByte())

        vertices += listOf(x, y, z, s)
        vertices += listOf(x + w, y + h, z, s)
        vertices += listOf(x + w, y, z, s)
        if (h >= 0.0f)
            triangles += listOf(3.toByte(), 4.toByte(), 5.toByte())
        else
            triangles += listOf(3.toByte(), 5.toByte(), 4.toByte())

        for (i in 0..5) {
            normals += listOf(0.0f, 0.0f, 1.0f)
        }

        glMaterialfv(GL_FRONT_AND_BACK, GL_DIFFUSE, cValuesOf(color.x, color.y, color.z, 1.0f))
        glMaterialfv(GL_FRONT_AND_BACK, GL_SPECULAR, cValuesOf(0.1f, 0.1f, 0.1f, 1.0f))
        glMaterialf(GL_FRONT_AND_BACK, GL_SHININESS, 0.0f)
        glVertexPointer(4, GL_FLOAT, 0, vertices.toFloatArray().toCValues().getPointer(this))
        glNormalPointer(GL_FLOAT, 0, normals.toFloatArray().toCValues().getPointer(this))
        glDrawElements(GL_TRIANGLES, triangles.size, GL_UNSIGNED_BYTE, triangles.toByteArray().toCValues().getPointer(this))
    }

    private val scale = 2.0f

    fun draw() = memScoped {
        if (!initialized) return

        stats.refresh()

        glPushMatrix()

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        glFrontFace(GL_CCW)

        val colors = arrayOf(Vector3(0.0f, 0.8f, 0.8f), Vector3(0.8f, 0.0f, 0.8f), Vector3(0.8f, 0.0f, 0.0f), Vector3(0.0f, 0.8f, 0.0f), Vector3(0.0f, 0.0f, 0.8f))
        var x = -1.0f
        val margin = (2.0f - 5 * 0.25f) / 4.0f
        var maxCount = stats.counts.max() ?: 0
        if (maxCount == 0) maxCount = 1
        for (i in colors.indices) {
            drawRect(x, 1.0f, 0.25f, -(0.01f + (stats.counts[i].toFloat() / maxCount * 0.5f)), 2.0f, 2.0f, colors[i])
            x = x + 0.25f + margin
        }

        glMatrixMode(GL_MODELVIEW)

        glTranslatef(0.0f, -0.5f, 0.0f)

        drawRect(-1.0f, -1.0f, 2.0f, 2.0f, 2.0f, 2.0f, colors[stats.myColor])

        glEnableClientState(GL_VERTEX_ARRAY)
        glEnableClientState(GL_NORMAL_ARRAY)
        glEnableClientState(GL_TEXTURE_COORD_ARRAY)

        val vertices = mutableListOf<Float>()
        val triangles = mutableListOf<Byte>()
        val normals = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()

        val poly = KotlinLogo(0.5f, 0.167f)
        for (f in poly.faces.indices) {
            val face = poly.faces[f]
            val u = poly.vertices[face.indices[2]] - poly.vertices[face.indices[1]]
            val v = poly.vertices[face.indices[0]] - poly.vertices[face.indices[1]]
            var normal = u.crossProduct(v).normalized() * -1.0f


            val copiedFace = ByteArray(face.indices.size)
            for (j in face.indices.indices) {
                copiedFace[j] = (vertices.size / 4).toByte()
                poly.vertices[face.indices[j]].copyCoordinatesTo(vertices)
                vertices.add(scale)
                normal.copyCoordinatesTo(normals)
                face.texCoords[j].copyCoordinatesTo(texCoords)
            }

            for (j in 1..face.indices.size - 2) {
                triangles.add(copiedFace[0])
                triangles.add(copiedFace[j])
                triangles.add(copiedFace[j + 1])
            }
        }

        glMultMatrixf(matrix)

        glMaterialfv(GL_FRONT_AND_BACK, GL_DIFFUSE, cValuesOf(0.8f, 0.8f, 0.8f, 1.0f))
        glMaterialfv(GL_FRONT_AND_BACK, GL_SPECULAR, cValuesOf(0.1f, 0.1f, 0.1f, 1.0f))
        glMaterialf(GL_FRONT_AND_BACK, GL_SHININESS, 0.0f)

        glVertexPointer(4, GL_FLOAT, 0, vertices.toFloatArray().toCValues().getPointer(this))
        glTexCoordPointer(2, GL_FLOAT, 0, texCoords.toFloatArray().toCValues().getPointer(this))
        glNormalPointer(GL_FLOAT, 0, normals.toFloatArray().toCValues().getPointer(this))
        glDrawElements(GL_TRIANGLES, triangles.size, GL_UNSIGNED_BYTE, triangles.toByteArray().toCValues().getPointer(this))

        glPopMatrix()

        if (eglSwapBuffers(display, surface) == 0) {
            logError("eglSwapBuffers() returned error ${eglGetError()}")
            destroy()
        }
    }

    fun destroy() {
        if (!initialized) return

        logInfo("Destroying context..")

        eglMakeCurrent(display, null, null, null)
        context?.let { eglDestroyContext(display, it) }
        surface?.let { eglDestroySurface(display, it) }
        eglTerminate(display)

        display = null
        surface = null
        context = null
        initialized = false

        arena.clear()
    }
}
