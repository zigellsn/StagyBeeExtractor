/*
 * Copyright 2019-2022 Simon Zigelli
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

package com.ze.stagybee.extractor.simulation

import com.ze.stagybee.extractor.Extractor
import com.ze.stagybee.extractor.Name
import com.ze.stagybee.extractor.Names
import io.ktor.client.statement.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.random.Random
import kotlin.random.nextInt

class SimulationExtractor : Extractor {

    private val validCharsUpper: List<Char> = ('A'..'Z').toList()
    private val validCharsLower: List<Char> = ('a'..'z').toList()
    override suspend fun login(): HttpResponse? {
        return null
    }


    @OptIn(InternalCoroutinesApi::class)
    override suspend fun getListeners(token: String, block: suspend (Names) -> Unit) {
        names().collect {
            block(it)
        }
    }

    override suspend fun stopListener() {
    }

    override suspend fun getListenersSnapshot(): Names {
        val names = mutableListOf<Name>()
        if (Random.nextBoolean()) {
            for (i in 0 until Random.nextInt(0..20)) {
                val name = Name(
                    0,
                    randomString(Random.nextInt(20)),
                    randomString(Random.nextInt(20)),
                    listenerType = Random.nextInt(3..4)
                )
                name.requestToSpeak = Random.nextBoolean()
                name.speaking = Random.nextBoolean()
                name.listenerCount = Random.nextInt(1..9)
                names.add(name)
            }
        }
        delay(Random.nextLong(1000L, 5000L))
        return Names(names)
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun names() = flow {
        while (true)
            emit(getListenersSnapshot())
    }

    private fun randomString(length: Int) =
        validCharsUpper.random() +
                (1 until length).map { validCharsLower.random() }
                    .joinToString("")
}
