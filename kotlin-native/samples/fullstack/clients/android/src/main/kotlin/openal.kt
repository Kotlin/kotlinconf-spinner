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

private fun CPointer<ByteVar>.toKString(length: Int): String {
    val bytes = this.readBytes(length)
    return kotlin.text.fromUtf8Array(bytes, 0, bytes.size)
}

class OpenAL(val nativeActivity: ANativeActivity) {
    var device: CPointer<ALCdevice>? = null
    var context: CPointer<ALCcontext>? = null
    val arena = Arena()

    fun initialize() {
        logInfo("Initializing Open AL...")
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
    }

    private class WAVEHeader(val rawPtr: NativePtr) {
        inline fun <reified T : CPointed> memberAt(offset: Long): T {
            return interpretPointed<T>(this.rawPtr + offset)
        }

        inline fun <reified T : CVariable> arrayMemberAt(offset: Long): CArrayPointer<T> {
            return interpretCPointer<T>(this.rawPtr + offset)!!
        }

        val riff get() = arrayMemberAt<ByteVar>(0)
        val riffSize get() = memberAt<IntVar>(4).value
        val wave get() = arrayMemberAt<ByteVar>(8)
        val fmt get() = arrayMemberAt<ByteVar>(12)
        val fmtSize get() = memberAt<IntVar>(16).value
        val format get() = memberAt<ShortVar>(20).value.toInt()
        val channels get() = memberAt<ShortVar>(22).value.toInt()
        val samplesPerSec get() = memberAt<IntVar>(24).value
        val bytesPerSec get() = memberAt<IntVar>(28).value
        val blockAlign get() = memberAt<ShortVar>(32).value.toInt()
        val bitsPerSample get() = memberAt<ShortVar>(34).value.toInt()
        val data get() = arrayMemberAt<ByteVar>(36)
        val dataSize get() = memberAt<IntVar>(40).value

        val rawData get() = arrayMemberAt<ByteVar>(44)
    }

    var source: ALuintVar? = null
    var buffer: ALuintVar? = null

    fun readWave(assetName: String) = memScoped {
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
            return
        }
        val header = WAVEHeader(buf.rawValue)
        if (header.riff.toKString(4) != "RIFF") {
            logError("Error parsing wav file 1")
            return
        }
        if (header.wave.toKString(4) != "WAVE") {
            logError("Error parsing wav file 2")
            return
        }
        if (header.fmt.toKString(4) != "fmt ") {
            logError("Error parsing wav file 3")
            return
        }
        if (header.data.toKString(4) != "data") {
            logError("Error parsing wav file 4")
            return
        }
        val rawData = arena.allocArray<ByteVar>(header.dataSize)
        memcpy(rawData, header.rawData, header.dataSize.signExtend())

        buffer = arena.alloc<ALuintVar>()
        val format = when (header.bitsPerSample) {
            8 -> if (header.channels == 1) AL_FORMAT_MONO8 else AL_FORMAT_STEREO8
            16 -> if (header.channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
            else -> 0
        }
        alGenBuffers(1, buffer!!.ptr)
        alBufferData(buffer!!.value, format, rawData, header.dataSize, header.samplesPerSec)

        logInfo("Wav successfully parsed and converted to OpenAL buffer")
    }

    fun play() {
        buffer?.let {
            if (source == null) {
                source = arena.alloc<ALuintVar>()
                alGenSources(1, source!!.ptr)
                logInfo("Successfully created OpenAL source")
            }
            alSourceQueueBuffers(source!!.value, 1, it.ptr)
            logInfo("Successfully queued audio source")
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
        arena.clear()
    }
}

