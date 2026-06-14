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

/** The envelope version this client speaks. Must match the host. */
const val PROTOCOL_VERSION = 1

/**
 * Maximum Unicode code points per text chunk. Small enough that progress advances
 * visibly on long pastes, large enough to stay well within the host's per-minute
 * request budget.
 */
const val CHUNK_MAX_CODE_POINTS = 200

/**
 * Split [text] into ordered chunks of at most [maxCodePoints] Unicode code points.
 *
 * Splitting on code-point boundaries guarantees a multi-unit character (an emoji, a
 * surrogate pair) is never cut across two chunks. Because the host types chunks
 * strictly in order, the typed result is identical to typing [text] unsplit.
 */
fun chunkText(text: String, maxCodePoints: Int = CHUNK_MAX_CODE_POINTS): List<String> {
    require(maxCodePoints > 0) { "maxCodePoints must be positive" }
    if (text.isEmpty()) return emptyList()

    val chunks = mutableListOf<String>()
    val builder = StringBuilder()
    var countInChunk = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val units = Character.charCount(codePoint)
        builder.append(text, index, index + units)
        index += units
        countInChunk++
        if (countInChunk == maxCodePoints) {
            chunks.add(builder.toString())
            builder.setLength(0)
            countInChunk = 0
        }
    }
    if (builder.isNotEmpty()) {
        chunks.add(builder.toString())
    }
    return chunks
}

/**
 * Where a sent message is in its lifecycle. Input is only "delivered" once the host
 * has acknowledged every chunk; the UI must not clear the user's text before then.
 */
sealed class DeliveryState {
    /** Nothing in flight. */
    object Idle : DeliveryState()

    /** [ackedChunks] of [totalChunks] confirmed so far; drives the progress indicator. */
    data class Sending(val id: String, val ackedChunks: Int, val totalChunks: Int) : DeliveryState()

    /** Every chunk acknowledged; the text may now be cleared. */
    data class Delivered(val id: String) : DeliveryState()

    /** Delivery did not complete; the text is retained and [retryable] says whether a retry can help. */
    data class Failed(val id: String, val reason: String, val retryable: Boolean) : DeliveryState()
}
