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

import platform.posix.M_PI

// Note: all coordinates are normed by the screen size
// and grow when going from bottom-left corner to top-right one;
// so bottom-left is `(0, 0)` and top-right is `(w/d, h/d)`.
class TouchControl(val gameState: GameState) {

    private var previous = Vector2.Zero

    /**
     * To be called when the screen gets touched.
     */
    fun down() {
        previous = Vector2.Zero
    }

    /**
     * To be called when finger moves;
     *
     * @param total the vector from the point where the screen have got touched to the current one.
     */
    fun move(total: Vector2) {
        val delta = total - previous

        // Don't apply micro movements immediatley, accumulate them instead:
        if (delta.length < 0.01f) return

        previous = total

        val (axis, angle) = movementToRotation(delta)
        gameState.manualRotate(axis, angle)
    }

    fun up(total: Vector2, timeElapsed: Float) {
        val velocity = total / (timeElapsed + 1e-9f)
        val rotationPerSecond = movementToRotation(velocity)

        val axis = rotationPerSecond.axis
        if (axis.length < 0.01f) return

        val angularSpeed = minOf(rotationPerSecond.angle, 3.0f * M_PI.toFloat())
        gameState.startIntertialRotation(axis, angularSpeed, angularAcceleration = -M_PI.toFloat())
    }

    private data class Rotation(val axis: Vector2, val angle: Float)

    private fun movementToRotation(movement: Vector2) =
            Rotation(
                    axis = Vector2(-movement.y, movement.x), // The normal to the movement.
                    // Moving finger from one corner to the opposite one rotates the model by 180 degrees:
                    angle = movement.length * M_PI.toFloat()
            )
}
