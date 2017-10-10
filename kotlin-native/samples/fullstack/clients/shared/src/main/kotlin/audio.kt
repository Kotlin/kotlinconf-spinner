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
    return kotlin.text.fromUtf8Array(bytes, 0, bytes.size)
}

class SoundPlayerImpl(val resourceName: String): SoundPlayer {
    var device: CPointer<ALCdevice>? = null
    var context: CPointer<ALCcontext>? = null
    var source: ALuintVar? = null
    var buffer: ALuintVar? = null

    val arena = Arena()
    var rawWAV: CArrayPointer<ByteVar>? = null
    var rawWAVSize = 0
    var samplesPerSec = 0
    var format = 0

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
            rawWAV = arena.allocArray<ByteVar>(header.dataSize)
            memcpy(rawWAV, header.rawData, header.dataSize.signExtend())
            rawWAVSize = header.dataSize.toInt()
            samplesPerSec = header.samplesPerSec

            format = when (header.bitsPerSample) {
                8 -> if (header.channels == 1) AL_FORMAT_MONO8 else AL_FORMAT_STEREO8
                16 -> if (header.channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
                else -> 0
            }
            println("Wav successfully parsed")
        }
    }

    fun initialize() {
        deinit()
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
        rawWAV?.let {
            buffer = arena.alloc<ALuintVar>()
            alGenBuffers(1, buffer!!.ptr)
            alBufferData(buffer!!.value, format, it, rawWAVSize.signExtend(), samplesPerSec)
        }
    }

    override fun play() {
        stop()
        buffer?.let {
            if (source == null) {
                source = arena.alloc<ALuintVar>()
                alGenSources(1, source!!.ptr)
            }
            alSourceQueueBuffers(source!!.value, 1, it.ptr)
            alSourcePlay(source!!.value)
        }
    }

    fun stop() {
        if (source == null) return
        buffer?.let {
            alSourceStop(source!!.value)
            alSourceUnqueueBuffers(source!!.value, 1, it.ptr)
        }
    }

    fun deinit() {
        stop()
        source?.let { alDeleteSources(1, it.ptr) }
        buffer?.let { alDeleteBuffers(1, it.ptr) }

        alcMakeContextCurrent(null)
        context?.let { alcDestroyContext(it) }
        device?.let { alcCloseDevice(it) }
    }
}

