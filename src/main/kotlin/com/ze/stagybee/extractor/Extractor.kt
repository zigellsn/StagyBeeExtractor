/*
 * Copyright 2019 Simon Zigelli
 *
 * Licensed under the Apache License, Version 3.0 (the "License");
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

package com.ze.stagybee.extractor

import io.ktor.http.cio.websocket.CloseReason

data class Name(
    val id: Int, val familyName: String, val givenName: String, var requestToSpeak: Boolean = false,
    var speaking: Boolean = false
) {
    var listenerCount: Int = 0
}

data class Names(val names: List<Name>)

interface Extractor {
    suspend fun login()
    suspend fun getListeners(block: suspend (Names) -> Unit): CloseReason?
    suspend fun stopListener()
    suspend fun getListenersSnapshot(): Names
}
