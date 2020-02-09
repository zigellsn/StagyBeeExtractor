/*
 * Copyright 2020 Simon Zigelli
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

import com.ze.stagybee.extractor.Name
import com.ze.stagybee.extractor.Names
import com.ze.stagybee.extractor.WebExtractor
import kotlinx.coroutines.delay
import kotlin.random.Random

class SimulationExtractor : WebExtractor() {

    private val validChars: List<Char> = ('a'..'z') + ('A'..'Z')

    override suspend fun logoff() {
    }

    override suspend fun getNames(): Names {
        val names = mutableListOf<Name>()
        if (Random.nextBoolean()) {
            for (i in 0 until Random.nextInt(0, 20)) {
                val name = Name(
                    0,
                    randomString(Random.nextInt(20)),
                    randomString(Random.nextInt(20))
                )
                name.requestToSpeak = Random.nextBoolean()
                name.speaking = Random.nextBoolean()
                name.listenerCount = Random.nextInt(1, 9)
                names.add(name)
            }
        }
        delay(Random.nextLong(1000L, 5000L))
        return Names(names)
    }

    private fun randomString(length: Int) =
        (1..length).map { Random.nextInt(0, validChars.size) }
            .map(validChars::get)
            .joinToString("")

    override suspend fun shutdownExtractor() {
    }

}
