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
import kotlin.system.*
import platform.posix.*
import kommon.machineName
import kurl.*
import kjson.*
import konan.worker.*

fun logError(message: String) {
    __android_log_write(ANDROID_LOG_ERROR, "KonanActivity", message)
}

fun logInfo(message: String) = println(message)

val errno: Int
    get() = posix_errno()

fun getUnixError() = strerror(errno)!!.toKString()

fun getTime() = kotlin.system.getTimeMicros() / 1_000_000.0

const val LOOPER_ID_INPUT = 2

const val LOOPER_ID_SENSOR = 3

fun <T : CPointed> CPointer<*>?.dereferenceAs(): T = this!!.reinterpret<T>().pointed

class Engine(val arena: NativePlacement, val state: NativeActivityState) {

    private val statsFetcher = StatsFetcherImpl(state.activity!!.pointed).also {
        it.asyncFetch()
    }

    private val gameState = GameState(SceneState(), statsFetcher)
    private val touchControl = TouchControl(gameState)

    private val renderer = Renderer(state.activity!!.pointed)
    private var queue: CPointer<AInputQueue>? = null
    private var sensorQueue: CPointer<ASensorEventQueue>? = null
    private var sensor: CPointer<ASensor>? = null
    private var rendererState: COpaquePointer? = null
    private var lastUpdateTime = 0.0
    private var needRedraw = true
    private var startTime = 0.0f
    private var prevTime = 0.0f
    private var startPoint = Vector2.Zero
    private var prevPoint = Vector2.Zero
    private var diagonal = 0.0f

    fun initSensors() {
        val sensorManager = ASensorManager_getInstance()
        if (sensorQueue == null) {
            sensorQueue = ASensorManager_createEventQueue(
                    sensorManager, state.looper, LOOPER_ID_SENSOR, null /* no callback */, null /* no data */)
        }
        sensor = ASensorManager_getDefaultSensor(sensorManager, ASENSOR_TYPE_ACCELEROMETER)
        if (sensor != null) {
            println("Accelerometer found")
            ASensorEventQueue_setEventRate(sensorQueue, sensor, 100000)
        } else {
            println("No accelerometer found")
        }
    }

    fun mainLoop() {
        initSensors()
        memScoped {
            val fd = alloc<IntVar>()
            while (true) {
                // Process events.
                eventLoop@ while (true) {
                    val id = ALooper_pollAll(if ((gameState.isAnimating() || needRedraw) && renderer.initialized) 0 else -1, fd.ptr, null, null)
                    if (id < 0) break@eventLoop
                    when (id) {
                        LOOPER_ID_SYS -> {
                            if (!processSysEvent(fd))
                                return // An error occured.
                        }

                        LOOPER_ID_INPUT -> processUserInput()

                        LOOPER_ID_SENSOR -> processSensorInput()
                    }
                }
                val currentTime = getTime()
                gameState.update((currentTime - lastUpdateTime).toFloat())
                lastUpdateTime = currentTime
                renderer.draw(gameState.sceneState)
            }
        }
    }

    val pointerSize = CPointerVar.size

    private fun processSysEvent(fd: IntVar): Boolean = memScoped {
        val eventPointer = alloc<COpaquePointerVar>()
        val readBytes = read(fd.value, eventPointer.ptr, pointerSize.narrow()).toLong()
        if (readBytes != pointerSize) {
            logError("Failure reading event, $readBytes read: ${getUnixError()}")
            return true
        }
        try {
            val event = eventPointer.value.dereferenceAs<NativeActivityEvent>()
            when (event.eventKind) {
                NativeActivityEventKind.START -> {
                    println("NativeActivityEventKind.START")
                    needRedraw = true
                    ASensorEventQueue_enableSensor(sensorQueue, sensor)
                }

                NativeActivityEventKind.STOP -> {
                    println("NativeActivityEventKind.STOP")
                    ASensorEventQueue_disableSensor(sensorQueue, sensor)
                }

                NativeActivityEventKind.PAUSE -> {
                    println("NativeActivityEventKind.PAUSE")
                    ASensorEventQueue_disableSensor(sensorQueue, sensor)
                    needRedraw = false
                }

                NativeActivityEventKind.RESUME -> {
                    println("NativeActivityEventKind.RESUME")
                    ASensorEventQueue_enableSensor(sensorQueue, sensor)
                    needRedraw = true
                }

                NativeActivityEventKind.DESTROY -> {
                    println("NativeActivityEventKind.DESTROY")
                    rendererState?.let { free(it) }
                    rendererState = null
                    return false
                }

                NativeActivityEventKind.NATIVE_WINDOW_CREATED -> {
                    val windowEvent = eventPointer.value.dereferenceAs<NativeActivityWindowEvent>()
                    val window = windowEvent.window!!
                    val width = ANativeWindow_getWidth(window) * 1.0f
                    val height = ANativeWindow_getHeight(window) * 1.0f
                    diagonal = sqrtf(width * width + height * height)
                    if (!renderer.initialize(window))
                        return false
                    needRedraw = true
                }

                NativeActivityEventKind.INPUT_QUEUE_CREATED -> {
                    val queueEvent = eventPointer.value.dereferenceAs<NativeActivityQueueEvent>()
                    if (queue != null)
                        AInputQueue_detachLooper(queue)
                    queue = queueEvent.queue
                    AInputQueue_attachLooper(queue, state.looper, LOOPER_ID_INPUT, null, null)
                }

                NativeActivityEventKind.INPUT_QUEUE_DESTROYED -> {
                    val queueEvent = eventPointer.value.dereferenceAs<NativeActivityQueueEvent>()
                    AInputQueue_detachLooper(queueEvent.queue)
                }

                NativeActivityEventKind.NATIVE_WINDOW_DESTROYED -> {
                    if (sensor != null) {
                        ASensorEventQueue_disableSensor(sensorQueue, sensor)
                    }
                    renderer.destroy()
                }
            }
        } finally {
            notifySysEventProcessed()
        }
        return true
    }

    private fun getEventPoint(event: CPointer<AInputEvent>?, i: Int) =
            Vector2(AMotionEvent_getRawX(event, i.signExtend<size_t>()) / diagonal, -AMotionEvent_getRawY(event, i.signExtend<size_t>()) / diagonal)

    private fun getEventTime(event: CPointer<AInputEvent>?) =
            AMotionEvent_getEventTime(event) / 1_000_000_000.0f

    private fun processUserInput(): Unit = memScoped {
        val event = alloc<CPointerVar<AInputEvent>>()
        if (AInputQueue_getEvent(queue, event.ptr) < 0) {
            println("Failure reading input event")
            return
        }
        val eventType = AInputEvent_getType(event.value)
        if (eventType == AINPUT_EVENT_TYPE_MOTION) {
            val action = AKeyEvent_getAction(event.value) and AMOTION_EVENT_ACTION_MASK
            when (action) {
                AMOTION_EVENT_ACTION_DOWN -> {
                    startTime = getEventTime(event.value)
                    touchControl.down()
                    startPoint = getEventPoint(event.value, 0)
                    prevTime = startTime
                    prevPoint = startPoint
                }

                AMOTION_EVENT_ACTION_UP -> {
                    val endPoint = getEventPoint(event.value, 0)
                    val endTime = getEventTime(event.value)
                    touchControl.up(endPoint - startPoint, endTime - startTime + 1e-9f)
                }

                AMOTION_EVENT_ACTION_MOVE -> {
                    val numberOfPointers = AMotionEvent_getPointerCount(event.value).toInt()
                    val curPoint = getEventPoint(event.value, numberOfPointers - 1)
                    val curTime = getEventTime(event.value)

                    if ((curPoint - prevPoint).length / (curTime - prevTime + 1e-9f) < 7) {
                        touchControl.move(curPoint - startPoint)
                        prevPoint = curPoint
                        prevTime = curTime
                    }
                }
            }
        }
        AInputQueue_finishEvent(queue, event.value, 1)
    }

    var shakeTimestamp = 0.0

    private fun processSensorInput(): Unit = memScoped {
        if (sensorQueue == null) return
        val event = alloc<ASensorEvent>()
        val now = getTime()
        while (ASensorEventQueue_getEvents(sensorQueue, event.ptr, 1) > 0) {
            val a = event.acceleration
            val force = sqrtf(a.x * a.x + a.y * a.y + a.z * a.z) / ASENSOR_STANDARD_GRAVITY
            if (force < 2.5f || shakeTimestamp + 1.0 > now) {
                continue
            }
            shakeTimestamp = now
            val accelerationWithGravity = Vector3(
                    a.x / ASENSOR_STANDARD_GRAVITY, a.y / ASENSOR_STANDARD_GRAVITY, a.z / ASENSOR_STANDARD_GRAVITY)

            touchControl.shake(guessUserAcceleration(accelerationWithGravity))
        }
    }

    fun guessUserAcceleration(accelerationWithGravity: Vector3): Vector3 {
        val array3 = FloatArray(3)
        array3[0] = accelerationWithGravity.x
        array3[1] = accelerationWithGravity.y
        array3[2] = accelerationWithGravity.z
        var gravityIndex = -1
        var gravitySign = 0
        for (i in 0 .. 2) {
            if (fabsf(array3[i] - 1.0f) < 0.2f) {
                gravityIndex = i
                gravitySign = if (array3[i] > 0) 1 else -1
            }
        }
        if (gravityIndex >= 0)
            array3[gravityIndex] = array3[gravityIndex] - 1.0f * gravitySign

        return Vector3(array3[0], array3[1], array3[2])
    }
}
