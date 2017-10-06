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
import objc.*

// This file contains a very primitive and coarse abstraction for OpenGL operations;
// it doesn't pretend to be universal or high-performance.

open class GlShaderProgram(vertexShaderSource: String, fragmentShaderSource: String) {
    val vertexArrayObject = memScoped {
        val resultVar = alloc<GLuintVar>()
        glGenVertexArrays(1, resultVar.ptr)
        resultVar.value
    }

    val program = glCreateProgram()

    init {
        glAttachShader(program, compileGlShader(GL_VERTEX_SHADER, vertexShaderSource))
        glAttachShader(program, compileGlShader(GL_FRAGMENT_SHADER, fragmentShaderSource))
        glLinkProgram(program)
        checkGlError()
    }

    open inner class Attribute(name: String) {
        val location = glGetAttribLocation(this@GlShaderProgram.program, name)
        private val buffer = createGlBuffer()

        protected fun assign(dim: Int, values: FloatArray) {
            glBindBuffer(GL_ARRAY_BUFFER, this.buffer)
            glBufferData(
                    GL_ARRAY_BUFFER,
                    (values.size * 4).signExtend(),
                    values.refTo(0),
                    GL_STREAM_DRAW
            )

            glEnableVertexAttribArray(this.location)
            glVertexAttribPointer(this.location, dim, GL_FLOAT, GL_FALSE.narrow(), 0, null)
        }
    }

    inner class FloatAttribute(name: String) : Attribute(name) {
        fun assign(values: FloatArray) = this.assign(1, values)
    }

    inner class Vector2Attribute(name: String) : Attribute(name) {
        fun assign(values: List<Vector2>) = this.assign(2, values.flatten())
    }

    inner class Vector3Attribute(name: String) : Attribute(name) {
        fun assign(values: List<Vector3>) = this.assign(3, values.flatten())
    }

    open inner class Uniform(name: String) {
        val location = glGetUniformLocation(this@GlShaderProgram.program, name)
    }

    inner class Vector2Uniform(name: String) : Uniform(name) {
        fun assign(value: Vector2) = glUniform2f(this.location, value.x, value.y)
    }

    inner class Vector3Uniform(name: String) : Uniform(name) {
        fun assign(value: Vector3) = glUniform3f(this.location, value.x, value.y, value.z)
    }

    inner class Matrix4Uniform(name: String) : Uniform(name) {
        fun assign(value: Matrix4) =
                glUniformMatrix4fv(this.location, 1, GL_FALSE.narrow(), value.flatten().refTo(0))
    }

    fun activate() {
        glUseProgram(this.program)
        glBindVertexArray(vertexArrayObject)
    }
}

private fun compileGlShader(type: GLenum, source: String) = memScoped {
    val shader = glCreateShader(type)
    checkGlError()

    if (shader == 0) throw Error("Failed to create a shader")

    glShaderSource(shader, 1, cValuesOf(source.cstr.getPointer(memScope)), null)
    glCompileShader(shader)

    val statusVar = alloc<GLintVar>()
    glGetShaderiv(shader, GL_COMPILE_STATUS, statusVar.ptr)
    if (statusVar.value != GL_TRUE) {
        val logBuffer = allocArray<ByteVar>(512)
        glGetShaderInfoLog(shader, 512, null, logBuffer)
        throw Error("Shader compilation failed: ${logBuffer.toKString()}")
    }

    checkGlError()

    shader
}

private fun createGlBuffer() = memScoped {
    val bufferVar = alloc<GLuintVar>()
    glGenBuffers(1, bufferVar.ptr)
    checkGlError()
    bufferVar.value
}

fun checkGlError() {
    val error = glGetError()
    if (error != 0) {
        val errorString = when (error) {
            GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
            GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            else -> "unknown"
        }

        throw Error("GL error: 0x${error.toString(16)} ($errorString)")
    }
}
