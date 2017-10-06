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

data class Vector2(val x: Float, val y: Float) {
    val length get() = sqrtf(x * x + y * y)

    fun normalized(): Vector2 {
        val len = length
        return Vector2(x / len, y / len)
    }

    fun copyCoordinatesTo(arr: MutableList<Float>) {
        arr.add(x)
        arr.add(y)
    }

    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun times(other: Float) = Vector2(x * other, y * other)
    operator fun div(other: Float) = Vector2(x / other, y / other)

    companion object {
        val Zero = Vector2(0.0f, 0.0f)
    }
}

data class Vector3(val x: Float, val y: Float, val z: Float) {
    val length get() = sqrtf(x * x + y * y + z * z)

    fun crossProduct(other: Vector3): Vector3 =
            Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

    fun dotProduct(other: Vector3) = (x * other.x + y * other.y + z * other.z)

    fun normalized(): Vector3 {
        val len = length
        return Vector3(x / len, y / len, z / len)
    }

    fun copyCoordinatesTo(arr: MutableList<Float>) {
        arr.add(x)
        arr.add(y)
        arr.add(z)
    }

    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun times(other: Float) = Vector3(x * other, y * other,  z * other)
}

data class Vector4(val x: Float, val y: Float, val z: Float, val w: Float) {
    constructor(vector3: Vector3, w: Float) : this(vector3.x, vector3.y, vector3.z, w)

    fun copyCoordinatesTo(arr: MutableList<Float>) {
        arr.add(x)
        arr.add(y)
        arr.add(z)
        arr.add(w)
    }
}

fun List<Vector2>.flatten() = FloatArray(this.size * 2).also {
    this.forEachIndexed { index, vector ->
        it[index * 2 + 0] = vector.x
        it[index * 2 + 1] = vector.y
    }
}

fun List<Vector3>.flatten() = FloatArray(this.size * 3).also {
    this.forEachIndexed { index, vector ->
        it[index * 3 + 0] = vector.x
        it[index * 3 + 1] = vector.y
        it[index * 3 + 2] = vector.z
    }
}

fun List<Vector4>.flatten() = FloatArray(this.size * 4).also {
    this.forEachIndexed { index, vector ->
        it[index * 4 + 0] = vector.x
        it[index * 4 + 1] = vector.y
        it[index * 4 + 2] = vector.z
        it[index * 4 + 3] = vector.w
    }
}

data class Matrix3(val col1: Vector3, val col2: Vector3, val col3: Vector3) {

    operator fun times(other: Vector3) = Vector3(
            col1.x * other.x + col2.x * other.y + col3.x * other.z,
            col1.y * other.x + col2.y * other.y + col3.y * other.z,
            col1.z * other.x + col2.z * other.y + col3.z * other.z
    )

    operator fun times(other: Matrix3) = Matrix3(
            col1 = this * other.col1,
            col2 = this * other.col2,
            col3 = this * other.col3
    )
}

fun Vector3.rotate(axis: Vector3, angle: Float): Vector3 {
    val a = this
    val b = axis.normalized()
    val a1 = b * a.dotProduct(b)
    val a2 = a - a1

    // a = a1 + a2, where (a1 || b) and (a2 _|_ b)

    val a2p = b.crossProduct(a2)

    // a2 and a2p is the scaled orthonormal basis of the plane _|_ b

    // a2r is a2 rotated around b:
    val a2r = a2 * cosf(angle) + a2p * sinf(angle)

    return a1 + a2r
}

fun rotationMatrix(axis: Vector3, angle: Float): Matrix3 {
    // Take into account that
    //   M = M * I = (M * e1 | M * e2 | M * e3)

    val e1 = Vector3(1.0f, 0.0f, 0.0f)
    val e2 = Vector3(0.0f, 1.0f, 0.0f)
    val e3 = Vector3(0.0f, 0.0f, 1.0f)

    return Matrix3(
            col1 = e1.rotate(axis, angle),
            col2 = e2.rotate(axis, angle),
            col3 = e3.rotate(axis, angle)
    )
}

fun diagonalMatrix(d1: Float, d2: Float, d3: Float) = Matrix3(
        col1 = Vector3(d1, 0.0f, 0.0f),
        col2 = Vector3(0.0f, d2, 0.0f),
        col3 = Vector3(0.0f, 0.0f, d3)
)

data class Matrix4(val col1: Vector4, val col2: Vector4, val col3: Vector4, val col4: Vector4) {
    constructor(matrix3: Matrix3, col4: Vector4 = Vector4(0.0f, 0.0f, 0.0f, 1.0f)) : this(
            col1 = Vector4(matrix3.col1, 0.0f),
            col2 = Vector4(matrix3.col2, 0.0f),
            col3 = Vector4(matrix3.col3, 0.0f),
            col4 = col4
    )

    fun flatten(): FloatArray {
        val values = mutableListOf<Float>()
        col1.copyCoordinatesTo(values)
        col2.copyCoordinatesTo(values)
        col3.copyCoordinatesTo(values)
        col4.copyCoordinatesTo(values)
        return values.toFloatArray()
    }

    operator fun times(other: Vector4) = Vector4(
            col1.x * other.x + col2.x * other.y + col3.x * other.z + col4.x * other.w,
            col1.y * other.x + col2.y * other.y + col3.y * other.z + col4.y * other.w,
            col1.z * other.x + col2.z * other.y + col3.z * other.z + col4.z * other.w,
            col1.w * other.x + col2.w * other.y + col3.w * other.z + col4.w * other.w
    )

    operator fun times(other: Matrix4) = Matrix4(
            col1 = this * other.col1,
            col2 = this * other.col2,
            col3 = this * other.col3,
            col4 = this * other.col4
    )
}

/**
 * The matrix to perform the orthographic projection from the world coordinate system to the clip space,
 * as described in [glOrtho documentation](https://www.khronos.org/registry/OpenGL-Refpages/es1.1/xhtml/glOrtho.xml)
 */
fun orthographicProjectionMatrix(
        left: Float, right: Float,
        bottom: Float, top: Float,
        near: Float, far: Float
): Matrix4 {
    val m3 = diagonalMatrix(
            2 / (right - left),
            2 / (top - bottom),
            -2 / (far - near)
    )

    val tx = -(right + left)/(right - left)
    val ty = -(top + bottom)/(top - bottom)
    val tz = -(far + near)/(far - near)

    return Matrix4(m3, col4 = Vector4(tx, ty, tz, 1.0f))
}

fun translationMatrix(dx: Float, dy: Float, dz: Float) = Matrix4(
        matrix3 = diagonalMatrix(1.0f, 1.0f, 1.0f),
        col4 = Vector4(dx, dy, dz, 1.0f)
)
