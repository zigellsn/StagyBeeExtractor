package com.ze.stagybee.extractor

import kotlinx.coroutines.delay
import kotlin.random.Random

class SimulationExtractor : WebExtractor() {

    private val validChars: List<Char> = ('a'..'z') + ('A'..'Z')

    override fun login() {
    }

    override fun logoff() {
    }

    override suspend fun getNames(): Names {
        val names = mutableListOf<Name>()
        if (Random.nextBoolean()) {
            for (i in 0 until Random.nextInt(0, 20)) {
                val name = Name(
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

    override fun shutdownExtractor() {
    }

    override fun initDriver() {
    }
}