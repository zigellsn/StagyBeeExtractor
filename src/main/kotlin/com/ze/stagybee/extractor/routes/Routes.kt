/*
 * Copyright 2019-2021 Simon Zigelli
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

package com.ze.stagybee.extractor.routes

import com.github.zigellsn.webhookk.add
import com.ze.stagybee.extractor.Names
import com.ze.stagybee.extractor.http.HttpExtractor
import com.ze.stagybee.extractor.simulation.SimulationExtractor
import com.ze.stagybee.extractor.webhooks
import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigInteger
import java.net.ConnectException
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

const val xStagyBeeExtractorEvent = "X-STAGYBEE-EXTRACTOR-EVENT"
const val xStagyBeeExtractorAction = "X-STAGYBEE-EXTRACTOR-ACTION"
const val eventListeners = "listeners"
const val eventStatus = "status"
const val eventError = "error"
const val actionSubscribe = "subscribe"
const val actionUnsubscribe = "unsubscribe"
const val actionStatus = "status"
const val actionMeta = "meta"

const val THREE_HOURS = 10_800_000L
const val QUARTER_HOUR = 900_000L

val extractors: ExtractorSessions = mutableMapOf()

fun Route.subscribe(env: String?, scope: CoroutineScope, dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    post("/subscribe") {
        call.response.headers.append(
            xStagyBeeExtractorAction,
            actionSubscribe
        )

        val subscribeRequest = try {
            call.receive<Subscribe>()
        } catch (e: Exception) {
            call.response.headers.append(xStagyBeeExtractorEvent, eventError)
            call.respond(HttpStatusCode.BadRequest, Success(false).apply { message = "Empty request body" })
            return@post
        }

        if (subscribeRequest.url.isEmpty()) {
            call.response.headers.append(xStagyBeeExtractorEvent, eventError)
            call.respond(HttpStatusCode.BadRequest, Success(false).apply { message = "URL must not be empty" })
            return@post
        }

        val url = try {
            Url(subscribeRequest.url)
        } catch (e: IllegalArgumentException) {
            call.response.headers.append(xStagyBeeExtractorEvent, eventError)
            call.respond(HttpStatusCode.BadRequest, Success(false).apply { message = e.toString() })
            return@post
        }

        val congregationId = createCongregationId(subscribeRequest)
        val sessionId = createSessionId(congregationId, url)
        if (!extractors.containsKey(congregationId)) {
            val timeout = createTimeout(subscribeRequest)
            extractors.putIfAbsent(congregationId,
                createExtractor(
                    env,
                    congregationId,
                    subscribeRequest,
                    timeout
                ).also {
                    it.listeners.putIfAbsent(sessionId, url)
                })
            applicationEngineEnvironment {
                log.trace("Connecting session $sessionId")
            }
            extractors[congregationId]?.job = scope.launch(dispatcher) {
                try {
                    withTimeout(
                        extractors[congregationId]?.timeoutSpan ?: error("Internal error")
                    ) {
                        startListener(
                            extractors[congregationId]
                                ?: error("Set to null by another thread"), congregationId
                        )
                    }
                } catch (e: Exception) {
                    applicationEngineEnvironment {
                        log.trace(e.toString())
                    }
                    terminateExtractor(sessionId)
                } finally {
                    terminateExtractor(sessionId)
                }
            }
        } else {
            if (extractors[congregationId]?.job?.isActive == true)
                triggerSnapshot(congregationId, url)
        }

        webhooks.topics.add(congregationId, url)
        call.respond(Success(true).apply { this.sessionId = sessionId })
    }
}

fun Route.unsubscribe() {
    delete("/unsubscribe/{sessionId}") {
        val sessionId = call.parameters["sessionId"] ?: error("Internal error")
        call.response.headers.append(
            xStagyBeeExtractorAction,
            actionUnsubscribe
        )

        if (!extractors.containsSessionId(sessionId)) {
            call.respond(HttpStatusCode.NotFound, Success(false).apply { message = "Unknown session id" })
            return@delete
        }

        stopExtractor(sessionId)
        call.respond(Success(true))
    }
}

fun Route.status() {
    get("/status/{sessionId}") {
        call.response.headers.append(
            xStagyBeeExtractorAction,
            actionStatus
        )
        val sessionId = call.parameters["sessionId"] ?: error("Session ID must not be empty")
        if (!extractors.containsSessionId(sessionId)) {
            call.respond(HttpStatusCode.NotFound, Status(running = false))
            return@get
        }
        val extractor = extractors.getBySessionId(sessionId) ?: return@get
        call.respond(
            Status(
                true
            ).apply {
                since = extractor.since
                remaining = extractor.remaining
                timeout = extractor.timeoutSpan
                serverTime = LocalDateTime.now()
            }
        )

    }
}

fun Route.meta() {
    get("/meta") {
        call.response.headers.append(
            xStagyBeeExtractorAction,
            actionMeta
        )
        call.respondFile(File("meta.json"))
    }
}

suspend fun startListener(extractor: ExtractorSession, id: String) {
    extractor.extractor.login()
    applicationEngineEnvironment {
        log.info("Running ID ${id}...")
    }
    triggerStatus(id, true, extractor)
    extractor.extractor.getListeners {
        triggerNames(id, it)
    }
    applicationEngineEnvironment {
        log.info("Stopping ID ${id}...")
    }
    triggerStatus(id, false, extractor)
}

private suspend fun triggerNames(id: String, it: Names) {
    try {
        webhooks.trigger(id) { url ->
            webhooks.post(
                url,
                TextContent(
                    Json { encodeDefaults = true }.encodeToString(it),
                    contentType = ContentType.Application.Json
                ),
                listOf(xStagyBeeExtractorEvent to listOf(eventListeners))
            ).execute()
        }
    } catch (e: ConnectException) {
        applicationEngineEnvironment {
            log.trace(e.toString())
        }
    }
}

private suspend fun triggerSnapshot(congregationId: CongregationId, url: Url) {
    try {
        webhooks.post(
            url,
            TextContent(
                Json { encodeDefaults = true }.encodeToString(
                    extractors[congregationId]?.extractor?.getListenersSnapshot() ?: Names(
                        emptyList()
                    )
                ), contentType = ContentType.Application.Json
            ),
            listOf(
                xStagyBeeExtractorEvent to listOf(
                    eventListeners
                )
            )
        )
    } catch (e: ConnectException) {
        applicationEngineEnvironment {
            log.trace(e.toString())
        }
    }
}

private suspend fun triggerStatus(id: String, status: Boolean, extractor: ExtractorSession) {
    try {
        webhooks.trigger(id) { url ->
            webhooks.post(
                url,
                TextContent(
                    Json { encodeDefaults = true }.encodeToString(
                        Status(status).apply {
                            since = extractor.since
                            remaining = extractor.remaining
                            timeout = extractor.timeoutSpan
                        }
                    ), contentType = ContentType.Application.Json
                ),
                listOf(
                    xStagyBeeExtractorEvent to listOf(
                        eventStatus
                    )
                )
            ).execute()
        }
    } catch (e: java.net.ConnectException) {
        applicationEngineEnvironment {
            log.trace(e.toString())
        }
    }
}

private suspend fun stopExtractor(sessionId: SessionId) {
    val extractor = extractors.getBySessionId(sessionId) ?: return
    val session = extractor.listeners[sessionId]
    val congregationId = extractors.filterValues { it == extractor }.keys.first()
    webhooks.topics[congregationId]?.remove(session)
    if (webhooks.topics[congregationId]?.count() == 0) {
        terminateExtractor(sessionId)
    }
}

private suspend fun terminateExtractor(sessionId: SessionId) {
    val extractor = extractors.getBySessionId(sessionId) ?: return
    val keys = extractors.filterValues { it == extractor }.keys
    if (keys.isEmpty())
        return
    val congregationId = keys.first()
    triggerStatus(congregationId, false, extractor)
    webhooks.topics.remove(congregationId)
    extractor.extractor.stopListener()
    try {
        if (extractor.job?.isActive == true) {
            extractor.job?.cancelAndJoin()
        }
    } catch (e: ClientEngineClosedException) {
        applicationEngineEnvironment {
            log.trace(e.toString())
        }
    }
    extractors.remove(congregationId)

    applicationEngineEnvironment {
        log.info("Stopped ID ${congregationId}...")
    }
}

private fun createExtractor(
    env: String?,
    congregationId: CongregationId,
    subscribe: Subscribe,
    timeout: Long
): ExtractorSession =
    if (env.isNullOrEmpty()) {
        ExtractorSession(
            HttpExtractor(
                congregationId,
                subscribe.congregation,
                subscribe.username,
                subscribe.password
            ),
            timeout
        )
    } else {
        ExtractorSession(SimulationExtractor(), timeout)
    }

private fun createTimeout(subscribeRequest: Subscribe) =
    if (subscribeRequest.timeout == -1L)
        THREE_HOURS
    else {
        if (subscribeRequest.timeout < QUARTER_HOUR) {
            QUARTER_HOUR
        }
        subscribeRequest.timeout
    }

private fun createSessionId(congregationId: CongregationId, url: Url): SessionId =
    when {
        !extractors.containsKey(congregationId) -> {
            UUID.randomUUID().toString()
        }
        extractors[congregationId]?.listeners?.containsValue(url) != true -> {
            UUID.randomUUID().toString()
        }
        else -> {
            extractors[congregationId]?.listeners?.filterValues { it == url }?.keys?.first()
                ?: error("Internal error")
        }
    }

private fun createCongregationId(subscribeRequest: Subscribe) =
    if (subscribeRequest.id.isEmpty() || subscribeRequest.id.length > 12) {
        val md: MessageDigest = MessageDigest.getInstance("SHA")
        BigInteger(
            1,
            md.digest("${subscribeRequest.congregation}:${subscribeRequest.username}:${subscribeRequest.password}".toByteArray())
        ).toString(16)
    } else {
        subscribeRequest.id
    }

private fun ExtractorSessions.containsSessionId(sessionId: SessionId): Boolean =
    this.filter { extractor ->
        extractor.value.listeners.contains(sessionId)
    }.isNotEmpty()

private fun ExtractorSessions.getBySessionId(sessionId: SessionId): ExtractorSession? {
    val collection = this.filter { extractor ->
        extractor.value.listeners.contains(sessionId)
    }.values
    return if (collection.isNotEmpty())
        collection.first()
    else
        null
}