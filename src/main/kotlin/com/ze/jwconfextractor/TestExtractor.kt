package com.ze.jwconfextractor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.lang.System.currentTimeMillis
import java.time.LocalDateTime
import kotlin.random.Random

class TestExtractor(
    id: String? = "",
    congregation: String? = "",
    username: String? = "",
    password: String? = "",
    private val frequency: Long = 5000L,
    private val timeout: Long = 1080000L
) : WebExtractor(id, congregation, username, password, frequency, timeout) {

    private val validChars: List<Char> = ('a'..'z') + ('A'..'Z')
    private fun getNames(): Names {
        val names = mutableListOf<Name>()
        if (Random.nextBoolean()) {
            for (i in 0 until Random.nextInt(0, 20)) {
                val name = Name(randomString(Random.nextInt(20)), randomString(Random.nextInt(20)))
                name.requestToSpeak = Random.nextBoolean()
                name.speaking = Random.nextBoolean()
                name.listenerCount = Random.nextInt(1, 9)
                names.add(name)
            }
        }
        return Names(names)
    }

    private fun randomString(length: Int) =
        (1..length).map { _ -> Random.nextInt(0, validChars.size) }
            .map(validChars::get)
            .joinToString("")

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    override suspend fun getListeners() = flow {
        isActive = true
        t0 = currentTimeMillis() + timeout
        since = LocalDateTime.now()!!
        emit(isActive)
        var previousNames: Names? = null
        while (currentTimeMillis() < t0 && isActive) {
            val names = getNames()
            if (names != previousNames) {
                previousNames = names
                emit(names)
            }
            delay(Random.nextLong(1000L, 5000L))
            delay(frequency)
        }
        this.emit(isActive)
    }
}