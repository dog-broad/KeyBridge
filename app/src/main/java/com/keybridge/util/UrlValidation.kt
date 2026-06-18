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

package com.keybridge.util

import androidx.annotation.StringRes
import com.keybridge.R
import java.net.URI

/**
 * Returns a string-resource id explaining why [raw] is not a usable WebSocket server URL, or
 * null if it is acceptable. Empty input returns null (nothing to flag while the field is blank);
 * callers should also require non-blank input before enabling Connect.
 */
@StringRes
fun validateServerUrl(raw: String): Int? {
    val url = raw.trim()
    if (url.isEmpty()) return null
    if (!url.startsWith("ws://") && !url.startsWith("wss://")) return R.string.url_error_scheme
    return try {
        if (URI(url).host.isNullOrBlank()) R.string.url_error_host else null
    } catch (e: Exception) {
        R.string.url_error_invalid
    }
}

/** True when [raw] is a non-blank, valid server URL — the gate for the Connect action. */
fun isValidServerUrl(raw: String): Boolean = raw.isNotBlank() && validateServerUrl(raw) == null
