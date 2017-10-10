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
import platform.android.*
import platform.egl.*
import platform.gles2.*
import platform.gles3.*
import kurl.*
import kjson.*
import konan.worker.*

class Renderer(val nativeActivity: ANativeActivity, val playSound: Boolean = false) {

    private val arena = Arena()
    private var display: EGLDisplay? = null
    private var surface: EGLSurface? = null
    private var context: EGLContext? = null
    var initialized = false

    private lateinit var screen: Vector2
    private lateinit var gameRenderer: GameRenderer

    fun initialize(window: CPointer<ANativeWindow>): Boolean {
        with(arena) {
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
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE,
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
                val z = alloc<EGLintVar>()
                if (eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_RED_SIZE, r.ptr) != 0 &&
                        eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_GREEN_SIZE, g.ptr) != 0 &&
                        eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_BLUE_SIZE, b.ptr) != 0 &&
                        eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_DEPTH_SIZE, d.ptr) != 0 &&
                        eglGetConfigAttrib(display, supportedConfigs[configIndex], EGL_RENDERABLE_TYPE, z.ptr) != 0 &&
                        r.value == 8 && g.value == 8 && b.value == 8 && d.value >= 24 && (z.value and EGL_OPENGL_ES2_BIT) != 0) {
                    break
                }
                ++configIndex
            }
            if (configIndex >= numConfigs.value) {
                println("WARNING: desired context is not found")
                configIndex = 0
            }

            surface = eglCreateWindowSurface(display, supportedConfigs[configIndex], window, null)
            if (surface == null) {
                logError("eglCreateWindowSurface() returned error ${eglGetError()}")
                destroy()
                return false
            }

            val contextAttribs = cValuesOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE)

            context = eglCreateContext(display, supportedConfigs[configIndex], null, contextAttribs)
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

            gameRenderer = GameRenderer()

            initialized = true

            return true
        }
    }

    fun draw(sceneState: SceneState) {
        gameRenderer.render(sceneState, screen.x, screen.y)
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