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

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.zigellsn.webhookk.WebhookK
import com.github.zigellsn.webhookk.add
import com.ze.stagybee.extractor.http.HttpExtractor
import com.ze.stagybee.extractor.simulation.SimulationExtractor
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.ClientEngineClosedException
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import java.math.BigInteger
import java.net.ConnectException
import java.security.MessageDigest
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.*

const val xStagyBeeExtractorEvent = "X-STAGYBEE-EXTRACTOR-EVENT"
const val xStagyBeeExtractorAction = "X-STAGYBEE-EXTRACTOR-ACTION"
const val eventListeners = "listeners"
const val eventStatus = "status"
const val eventError = "error"
const val actionSubscribe = "subscribe"
const val actionUnsubscribe = "unsubscribe"
const val actionStatus = "status"
const val actionMeta = "meta"

const val THREE_HOURS = 1080000L
const val QUARTER_HOUR = 90000L

typealias CongregationId = String
typealias SessionId = String

data class Subscribe(
    val url: String,
    val id: CongregationId?,
    val congregation: String?,
    val username: String?,
    val password: String?,
    val timeout: Long?
)

data class Success(
    val success: Boolean,
    val message: String? = null,
    val sessionId: String? = null
)

data class Status(
    val running: Boolean = false,
    val since: LocalDateTime? = null,
    val remaining: Long? = null,
    val timeout: Long? = null,
    val serverTime: LocalDateTime = LocalDateTime.now()
)

data class ExtractorSession(
    val extractor: Extractor,
    val timeoutSpan: Long
) {
    private val timeoutTime = System.currentTimeMillis() + timeoutSpan
    val since: LocalDateTime = LocalDateTime.now()
    val remaining
        get() = if (job != null) timeoutTime - System.currentTimeMillis() else timeoutSpan
    var job: Job? = null
    val listeners: MutableMap<SessionId, Url> = mutableMapOf()
}

typealias ExtractorSessions = MutableMap<CongregationId, ExtractorSession>

lateinit var webhooks: WebhookK
val extractors: ExtractorSessions = mutableMapOf()
val json = jacksonObjectMapper()

class Proxy : CliktCommand() {

    private val proxyUrl: String? by option(help = "Proxy")
    private val serverPort: Int by option(help = "Port").int().default(8080)

    @InternalCoroutinesApi
    @ExperimentalCoroutinesApi
    @KtorExperimentalAPI
    override fun run() {
        val env = applicationEngineEnvironment {
            module {
                main()
            }
            // Test API
            connector {
                host = "127.0.0.1"
                port = 9090
            }
            // Public API
            connector {
                host = "0.0.0.0"
                port = serverPort
            }
        }
        webhooks = WebhookK(HttpClient(CIO) {
            engine {
                proxy = if (!proxyUrl.isNullOrEmpty())
                    ProxyBuilder.http(proxyUrl!!)
                else
                    ProxyConfig.NO_PROXY
            }
        })

        embeddedServer(Netty, env).start(true)
    }
}

fun main(args: Array<String>) = Proxy().main(args)

@InternalCoroutinesApi
@KtorExperimentalAPI
@ExperimentalCoroutinesApi
fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            dateFormat = DateFormat.getDateInstance()
        }
    }
    routing {
        post("/api/subscribe/") {
            call.response.headers.append(
                xStagyBeeExtractorAction,
                actionSubscribe
            )

            val subscribeRequest = try {
                call.receive<Subscribe>()
            } catch (e: MismatchedInputException) {
                call.response.status(HttpStatusCode.BadRequest)
                call.respond(Success(false, "Empty request body"))
                return@post
            }

            if (subscribeRequest.url.isEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
                call.respond(Success(false, "URL must not be empty"))
                return@post
            }

            val url = try {
                Url(subscribeRequest.url)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    Success(false, e.toString())
                )
                return@post
            }

            val congregationId = createCongregationId(subscribeRequest)
            val sessionId = createSessionId(congregationId, url)
            if (!extractors.containsKey(congregationId)) {
                val timeout = createTimeout(subscribeRequest)
                try {
                    extractors[congregationId] =
                        createExtractor(
                            call.request.local.port,
                            congregationId,
                            subscribeRequest,
                            timeout
                        ).also {
                            it.listeners[sessionId] = url
                        }
                } catch (err: Exception) {
                    call.response.headers.append(
                        xStagyBeeExtractorEvent,
                        eventError
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        Success(false, err.toString())
                    )
                    return@post
                } finally {
                    try {
                        extractors[congregationId]?.job = GlobalScope.launch {
                            withTimeout(
                                extractors[congregationId]?.timeoutSpan ?: throw AssertionError("Internal error")
                            ) {
                                startListener(
                                    extractors[congregationId]
                                        ?: throw AssertionError("Set to null by another thread"), congregationId
                                )
                            }
                        }
                    } finally {
                        extractors[congregationId]?.extractor?.stopListener()
                    }
                }
            } else {
                triggerSnapshot(congregationId)
            }

            webhooks.topics.add(congregationId, url)
            call.respond(Success(true, sessionId = sessionId))
        }
        delete("/api/unsubscribe/{sessionId}/") {
            val sessionId = call.parameters["sessionId"] ?: throw AssertionError("Internal error")
            call.response.headers.append(
                xStagyBeeExtractorAction,
                actionUnsubscribe
            )

            if (!extractors.containsSessionId(sessionId)) {
                call.respond(Success(false, "Unknown session id"))
                return@delete
            }

            stopExtractor(sessionId)
            call.respond(Success(true))
        }
        get("/api/status/{sessionId}/") {
            call.response.headers.append(
                xStagyBeeExtractorAction,
                actionStatus
            )
            val sessionId = call.parameters["sessionId"] ?: throw AssertionError("")
            if (!extractors.containsSessionId(sessionId)) {
                call.respond(Status(running = false))
                return@get
            }
            val extractor = extractors.getBySessionId(sessionId)
            call.respond(
                Status(
                    true,
                    extractor.since,
                    extractor.remaining,
                    extractor.timeoutSpan,
                    LocalDateTime.now()
                )
            )

        }
        get("/api/meta/") {
            call.response.headers.append(
                xStagyBeeExtractorAction,
                actionMeta
            )
            call.respondFile(File("meta.json"))
        }
    }
}

@InternalCoroutinesApi
@KtorExperimentalAPI
@ExperimentalCoroutinesApi
suspend fun startListener(extractor: ExtractorSession, id: String) {
    println("Running ID ${id}...")
    triggerStatus(id, true)
    extractor.extractor.getListeners {
        triggerNames(id, it)
    }
    println("Stopping ID ${id}...")
    triggerStatus(id, true)
}

@InternalCoroutinesApi
private suspend fun triggerNames(id: String, it: Any) {
    try {
        webhooks.trigger(id) { url ->
            webhooks.post(
                url,
                TextContent(json.writeValueAsString(it), contentType = ContentType.Application.Json),
                listOf(xStagyBeeExtractorEvent to listOf(eventListeners))
            ).execute()
        }.collect{  }
    } catch (e: ConnectException) {
        println(e.toString())
    }
}

@InternalCoroutinesApi
private suspend fun triggerSnapshot(congregationId: CongregationId) {
    try {
        webhooks.trigger(congregationId) { lUrl ->
            webhooks.post(
                lUrl,
                TextContent(
                    json.writeValueAsString(
                        extractors[congregationId]?.job?.isActive
                            ?: extractors[congregationId]?.extractor?.getListenersSnapshot() ?: Names(
                                emptyList()
                            )
                    ), contentType = ContentType.Application.Json
                ),
                listOf(
                    xStagyBeeExtractorEvent to listOf(
                        eventListeners
                    )
                )
            ).execute()
        }.collect{  }
    } catch (e: ConnectException) {
        print(e.toString())
    }
}

@InternalCoroutinesApi
private suspend fun triggerStatus(id: String, status: Boolean) {
    try {
        webhooks.trigger(id) { url ->
            webhooks.post(
                url,
                TextContent(
                    json.writeValueAsString(
                        Status(
                            status
                        )
                    ), contentType = ContentType.Application.Json
                ),
                listOf(
                    xStagyBeeExtractorEvent to listOf(
                        eventStatus
                    )
                )
            ).execute()
        }.collect{  }
    } catch (e: ConnectException) {
        println(e.toString())
    }
}

@InternalCoroutinesApi
private suspend fun stopExtractor(sessionId: SessionId) {
    val extractor = extractors.getBySessionId(sessionId)
    val session = extractor.listeners[sessionId]
    val congregationId = extractors.filterValues { it == extractor }.keys.first()
    webhooks.topics[congregationId]?.remove(session)
    if (webhooks.topics[congregationId]?.count() == 0) {
        webhooks.topics.remove(congregationId)
        extractor.extractor.stopListener()
        try {
            extractor.job?.cancelAndJoin()
        } catch (e: ClientEngineClosedException) {
            print(e.toString())
        }
        extractors.remove(congregationId)
        println("Stopped ID ${congregationId}...")
    }
}

private fun createExtractor(
    port: Int,
    congregationId: CongregationId,
    subscribe: Subscribe,
    timeout: Long
): ExtractorSession =
    if (port == 8080) {
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
    if (subscribeRequest.timeout == null)
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
                ?: throw AssertionError("Internal error")
        }
    }

private fun createCongregationId(subscribeRequest: Subscribe) =
    if (subscribeRequest.id.isNullOrEmpty() || subscribeRequest.id.length > 12) {
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

private fun ExtractorSessions.getBySessionId(sessionId: SessionId): ExtractorSession =
    this.filter { extractor ->
        extractor.value.listeners.contains(sessionId)
    }.values.first()