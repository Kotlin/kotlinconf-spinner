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

internal class WAVEHeader(val rawPtr: NativePtr) {
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