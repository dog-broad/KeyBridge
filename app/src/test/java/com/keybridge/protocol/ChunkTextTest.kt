/*
 * Copyright 2025 Rushyendra Guntupalli (dog-broad) and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.keybridge.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkTextTest {

    @Test
    fun emptyTextProducesNoChunks() {
        assertEquals(emptyList<String>(), chunkText(""))
    }

    @Test
    fun shortTextIsASingleChunk() {
        assertEquals(listOf("hello"), chunkText("hello", maxCodePoints = 200))
    }

    @Test
    fun chunksJoinBackToOriginal() {
        val text = "The quick brown fox jumps over the lazy dog. ".repeat(50)
        val chunks = chunkText(text, maxCodePoints = 64)
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun eachChunkHonoursTheCodePointBound() {
        val text = "x".repeat(1000)
        val chunks = chunkText(text, maxCodePoints = 100)
        assertEquals(10, chunks.size)
        assertTrue(chunks.all { it.codePointCount(0, it.length) <= 100 })
    }

    @Test
    fun neverSplitsASurrogatePair() {
        // Each emoji is a surrogate pair (two UTF-16 units, one code point).
        val emoji = "😀" // grinning face
        val text = emoji.repeat(5)
        val chunks = chunkText(text, maxCodePoints = 2)
        // 5 code points at 2 per chunk -> 3 chunks, and rejoining is lossless.
        assertEquals(3, chunks.size)
        assertEquals(text, chunks.joinToString(""))
        // No chunk ends on a lone high surrogate.
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) })
    }

    @Test
    fun countsByCodePointNotUtf16Unit() {
        val emoji = "😀"
        // 3 code points (emoji = 1) should fit in one chunk of max 3, despite being 6 UTF-16 units.
        val chunks = chunkText("a${emoji}b", maxCodePoints = 3)
        assertEquals(listOf("a${emoji}b"), chunks)
    }
}
