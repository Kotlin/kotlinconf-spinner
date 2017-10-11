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

enum class Team(val colorVector: Vector3) {
    CYAN(0.0f, 0.8f, 0.8f),
    MAGENTA(0.8f, 0.0f, 0.8f),
    RED(0.8f, 0.0f, 0.0f),
    GREEN(0.0f, 0.8f, 0.0f),
    BLUE(0.0f, 0.0f, 0.8f)
    ;

    constructor(r: Float, g: Float, b: Float) : this(Vector3(r, g, b))

    companion object {
        val count = Team.values().size
    }
}

class Stats(private val counts: IntArray, val myTeam: Team, val myContribution: Int) {
    fun getCount(team: Team): Int = counts[team.ordinal]
}

/**
 * The aspect of the game state enough to draw the scene.
 */
class SceneState(
        var rotationMatrix: Matrix3 = diagonalMatrix(1.0f, 1.0f, 1.0f),
        var stats: Stats? = null
) {

    fun rotate(axis: Vector2, angle: Float) {
        this.rotationMatrix = rotationMatrix(Vector3(axis.x, axis.y, 0.0f), angle) * this.rotationMatrix
    }
}

interface StatsFetcher {
    /**
     * Starts the async query to fetch the stats.
     */
    fun asyncFetch()

    /**
     * Starts the async query to submit a click and fetch the stats.
     *
     * @return `true` on success, or `false` if the call should be retried later.
     */
    fun asyncTryClickAndFetch(): Boolean

    /**
     * @return the [Stats] fetched most recently.
     */
    fun getMostRecentFetched(): Stats?
}

interface SoundPlayer {
    fun play()
}

object EmptyPlayer: SoundPlayer {
    override fun play() {
    }
}

/**
 * The entire game state including the velocity and acceleration.
 *
 * Note: all vectors are in world coordnates.
 */
class GameState(val sceneState: SceneState, val statsFetcher: StatsFetcher, val soundPlayer: SoundPlayer = EmptyPlayer) {
    private var rotationAngularSpeed: Float = 0.0f
    private var rotationAngularAcceleration: Float = 0.0f
    private var rotationAxis = Vector2.Zero
    private var accumulatedAngle: Float = 0.0f
    private val accumulatedAngleUnit: Float = 4 * M_PI.toFloat()

    private var timeSinceLastStatsRefresh = 1000.0f // Very long time ago, almost never.

    fun isAnimating() = rotationAngularSpeed > 0

    fun update(timeSinceLastUpdate: Float) {
        sceneState.stats = statsFetcher.getMostRecentFetched()

        if (rotationAngularSpeed > 0) {
            rotate(rotationAxis, rotationAngularSpeed * timeSinceLastUpdate)
            rotationAngularSpeed += timeSinceLastUpdate * rotationAngularAcceleration
        }

        timeSinceLastStatsRefresh += timeSinceLastUpdate
        if (timeSinceLastStatsRefresh > 5.0f) {
            // Fetch stats every 5 seconds:
            statsFetcher.asyncFetch()
            timeSinceLastStatsRefresh = 0.0f
        }
    }

    fun startIntertialRotation(axis: Vector2, angularSpeed: Float, angularAcceleration: Float) {
        this.rotationAxis = axis
        this.rotationAngularSpeed = angularSpeed
        this.rotationAngularAcceleration = angularAcceleration
    }

    fun stopIntertialRotation() {
        rotationAngularSpeed = -1.0f // < 0
    }

    fun manualRotate(axis: Vector2, angle: Float) {
        stopIntertialRotation()
        rotate(axis, angle)
    }

    private fun rotate(axis: Vector2, angle: Float) {
        accumulatedAngle += angle
        if (accumulatedAngle >= accumulatedAngleUnit && sceneState.stats != null &&
                statsFetcher.asyncTryClickAndFetch()) {

            accumulatedAngle -= accumulatedAngleUnit
            timeSinceLastStatsRefresh = 0.0f

            soundPlayer.play()
        }

        sceneState.rotate(axis, angle)
    }
}
