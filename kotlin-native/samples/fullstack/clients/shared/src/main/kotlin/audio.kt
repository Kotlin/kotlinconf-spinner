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
import platform.posix.*
import platform.OpenAL.*

private fun CPointer<ByteVar>.toKString(length: Int): String {
    val bytes = this.readBytes(length)
    return bytes.stringFromUtf8()
}

class SoundPlayerImpl(val resourceName: String): SoundPlayer {
    var device: CPointer<ALCdevice>? = null
    var context: CPointer<ALCcontext>? = null
    val source: ALuintVar
    val buffer: ALuintVar

    private val arena = Arena()
    private lateinit var rawWAV: ByteArray
    private var pinnedBuffer: Pinned<ByteArray>? = null
    var samplesPerSec = 0
    var format = 0
    var enabled = true

    init {
        val fileBytes = kommon.readResource(resourceName)

        fileBytes.usePinned {
            val fileBytesPtr = it.addressOf(0)
            val header = WAVEHeader(fileBytesPtr.rawValue)
            if (header.riff.toKString(4) != "RIFF") {
                logError("Error parsing wav file 1")
                return@usePinned
            }
            if (header.wave.toKString(4) != "WAVE") {
                logError("Error parsing wav file 2")
                return@usePinned
            }
            if (header.fmt.toKString(4) != "fmt ") {
                logError("Error parsing wav file 3")
                return@usePinned
            }
            if (header.data.toKString(4) != "data") {
                logError("Error parsing wav file 4")
                return@usePinned
            }
            rawWAV = ByteArray(header.dataSize)
            memcpy(rawWAV.refTo(0), header.rawData, rawWAV.size.convert())
            samplesPerSec = header.samplesPerSec

            format = when (header.bitsPerSample) {
                8 -> if (header.channels == 1) AL_FORMAT_MONO8 else AL_FORMAT_STEREO8
                16 -> if (header.channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
                else -> 0
            }
            println("Wav successfully parsed")
        }

        buffer = nativeHeap.alloc<ALuintVar>()
        source = nativeHeap.alloc<ALuintVar>()
    }

    fun enable(enabled: Boolean) {
        this.enabled = enabled
    }

    fun initialize() {
        println("Initializing Open AL...")
        device = alcOpenDevice(null)
        if (device == null) {
            logError("Unable to open audio device")
            return
        }
        context = alcCreateContext(device, null)
        if (context == null) {
            alcCloseDevice(device)
            logError("Unable to create audio context")
            return
        }
        alcMakeContextCurrent(context)
        alGenBuffers(1, buffer.ptr)
        pinnedBuffer = rawWAV.pin()
        alBufferData(buffer.value, format, pinnedBuffer!!.addressOf(0), rawWAV.size, samplesPerSec)
        alGenSources(1, source.ptr)

        alSourceQueueBuffers(source.value, 1, buffer.ptr)
    }

    override fun play() {
        if (!enabled) return
        stop()
        alSourcePlay(source.value)
    }

    fun stop() {
        alSourceStop(source.value)
    }

    fun deinit() {
        println("Deitializing Open AL...")

        stop()
        // Let playback finish.
        memScoped {
            val state = alloc<ALintVar>()
            do {
                alGetSourcei(source.value, AL_SOURCE_STATE, state.ptr)
            } while (state.value == AL_PLAYING)
        }

        alDeleteSources(1, source.ptr)
        alDeleteBuffers(1, buffer.ptr)

        alcMakeContextCurrent(null)
        context?.let { alcDestroyContext(it) }
        context = null
        device?.let { alcCloseDevice(it) }
        device = null

        pinnedBuffer?.let {
            it.unpin()
        }
        pinnedBuffer = null
    }
}

