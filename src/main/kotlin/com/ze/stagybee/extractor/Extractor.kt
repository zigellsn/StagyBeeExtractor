/*
 * Copyright 2019 Simon Zigelli
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

package com.ze.stagybee.extractor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime

data class ExtractorStatus(
    val running: Boolean,
    val since: LocalDateTime,
    val remaining: Long,
    val timeout: Long
)

data class Name(
    val familyName: String, val givenName: String, var requestToSpeak: Boolean = false,
    var speaking: Boolean = false
) {
    var listenerCount: Int = 0
}

abstract class WebExtractor(
    private val frequency: Long = 1000L,
    protected open val timeout: Long = 1080000L
) : Extractor {

    override fun stopListener() {
        isActive = false
    }

    private val t0 by lazy { System.currentTimeMillis() + timeout }
    private val since by lazy { LocalDateTime.now()!! }
    private var isActive: Boolean = false
    private val remaining
        get() = if (isActive) t0 - System.currentTimeMillis() else timeout
    override val status: ExtractorStatus
        get() = ExtractorStatus(isActive, since, remaining, timeout)

    protected abstract fun login()

    protected abstract fun logoff()

    protected abstract suspend fun getNames(): Names

    override suspend fun getListenersSnapshot(): Names? {
        return getNames()
    }

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    override suspend fun getListeners() = flow {
        initExtractor()
        var previousNames: Names? = null
        isActive = true
        emit(isActive)
        while (System.currentTimeMillis() < t0 && isActive) {
            val names = getNames()
            if (names != previousNames) {
                previousNames = names
                emit(names)
            }
            delay(frequency)
        }
        emit(isActive)
        shutdownExtractor()
    }

    protected open fun initExtractor() {
        initDriver()
    }

    protected open fun shutdownExtractor() {
        logoff()
    }

    protected abstract fun initDriver()
}

data class Names(val names: List<Name>)

interface Extractor {
    suspend fun getListenersSnapshot(): Names?
    suspend fun getListeners(): Flow<Any>
    fun stopListener()
    val status: ExtractorStatus
}