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
import com.ze.webhookk.WebhookK
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.*


data class Subscribe(
    val url: String,
    val id: String?,
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
    val serverTime: LocalDateTime? = null
)

const val xStagyBeeExtractorEvent = "X-STAGYBEE-EXTRACTOR-EVENT"
const val xStagyBeeExtractorAction = "X-STAGYBEE-EXTRACTOR-ACTION"
const val eventListeners = "listeners"
const val eventStatus = "status"
const val eventError = "error"
const val actionSubscribe = "subscribe"
const val actionUnsubscribe = "unsubscribe"
const val actionStatus = "status"
const val actionMeta = "meta"

const val FREQUENCY = 1000L
const val THREE_HOURS = 1080000L
const val QUARTER_HOUR = 90000L

lateinit var webhooks: WebhookK
val extractors = mutableMapOf<String, Extractor>()
val jobs = mutableMapOf<String, Job>()
val listeners = mutableMapOf<String, Pair<String, Url>>()
val json = jacksonObjectMapper()

@KtorExperimentalAPI
@ExperimentalCoroutinesApi
suspend fun startListener(extractor: Extractor, id: String) {
    val job = extractor.getListeners().onEach {
        if (it is Boolean) {
            if (it) {
                println("Running ID ${id}...")
                webhooks.trigger(
                    id,
                    TextContent(
                        json.writeValueAsString(
                            Status(
                                it
                            )
                        ), contentType = ContentType.Application.Json
                    ),
                    listOf(
                        xStagyBeeExtractorEvent to listOf(
                            eventStatus
                        )
                    )
                ).collect { }
            }
        } else {
            webhooks.trigger(
                id,
                TextContent(json.writeValueAsString(it), contentType = ContentType.Application.Json),
                listOf(xStagyBeeExtractorEvent to listOf(eventListeners))
            ).collect { }
        }
    }.launchIn(GlobalScope)
    jobs[id] = job
    //TODO: Send error
}

class Proxy : CliktCommand() {

    private val proxyUrl: String? by option(help = "Proxy")
    private val serverPort: Int by option(help = "Port").int().default(8080)
    private val driverBin: String? by option(help = "WebDriver Binary")

    @ExperimentalCoroutinesApi
    @KtorExperimentalAPI
    override fun run() {
        val env = applicationEngineEnvironment {
            module {
                main(driverBin)
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

@KtorExperimentalAPI
@ExperimentalCoroutinesApi
fun Application.main(driverBin: String?) {
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
            val topic = createTopic(subscribeRequest)
            val url = try {
                Url(subscribeRequest.url)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    Success(false, e.toString())
                )
                return@post
            }
            val sessionId = createSessionId(topic, url)

            if (!extractors.containsKey(topic)) {
                val timeout = createTimeout(subscribeRequest)
                try {
                    extractors[topic] =
                        createExtractor(
                            call.request.local.port,
                            topic,
                            subscribeRequest,
                            timeout,
                            driverBin
                        )
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
                    startListener(
                        extractors[topic]
                            ?: throw AssertionError("Set to null by another thread"), topic
                    )
                }
            } else {
                webhooks.trigger(
                    topic,
                    TextContent(
                        json.writeValueAsString(
                            jobs[topic]?.isActive ?: extractors[topic]?.getListenersSnapshot() ?: Names(
                                emptyList()
                            )
                        ), contentType = ContentType.Application.Json
                    ),
                    listOf(
                        xStagyBeeExtractorEvent to listOf(
                            eventListeners
                        )
                    )
                ).collect { }
            }

            webhooks.add(topic, url)
            call.respond(Success(true, sessionId = sessionId))
        }
        delete("/api/unsubscribe/{sessionId}/") {
            val sessionId = call.parameters["sessionId"]
            call.response.headers.append(
                xStagyBeeExtractorAction,
                actionUnsubscribe
            )

            if (!listeners.containsKey(sessionId)) {
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
            if (!listeners.containsKey(sessionId)) {
                call.respond(Status(running = false))
                return@get
            }
            val extractor = extractors[listeners[sessionId]?.first]
            if (extractor != null) {
                call.respond(
                    Status(
                        extractor.status.running,
                        extractor.status.since,
                        extractor.status.remaining,
                        extractor.status.timeout,
                        LocalDateTime.now()
                    )
                )
            } else
                call.respond(Status(false))
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

private suspend fun stopExtractor(sessionId: String?) {
    val session = listeners[sessionId] ?: throw AssertionError("")
    if (extractors.containsKey(session.first)) {
        webhooks.remove(session.first, session.second)
        if (webhooks.getUrls(session.first).count() == 0) {

            webhooks.remove(session.first)
            extractors[session.first]?.stopListener()
            jobs[session.first]?.cancelAndJoin()
            jobs.remove(session.first)
            extractors.remove(session.first)
            listeners.remove(sessionId)
            println("Stopped ID ${session.first}...")
        }
    }
}

private fun createExtractor(
    port: Int,
    topic: String,
    subscribe: Subscribe,
    timeout: Long,
    driverBin: String?
): Extractor =
    if (port == 8080) {
        JWConfExtractor(
            topic,
            subscribe.congregation,
            subscribe.username,
            subscribe.password,
            FREQUENCY,
            timeout,
            driverBin
        )
    } else {
        SimulationExtractor()
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

private fun createSessionId(topic: String, url: Url) =
    if (!listeners.containsValue(topic to url)) {
        val newSessionId = UUID.randomUUID().toString()
        listeners[newSessionId] = topic to url
        newSessionId
    } else {
        val sessions = listeners.filterValues { it == topic to url }.keys
        if (sessions.size == 1) {
            sessions.toList()[0]
        } else {
            //TODO: Exception
            throw Exception()
        }
    }

private fun createTopic(subscribeRequest: Subscribe) =
    if (subscribeRequest.id.isNullOrEmpty() || subscribeRequest.id.length > 12) {
        val md: MessageDigest = MessageDigest.getInstance("SHA")
        BigInteger(
            1,
            md.digest("${subscribeRequest.congregation}:${subscribeRequest.username}:${subscribeRequest.password}".toByteArray())
        ).toString(16)
    } else {
        subscribeRequest.id
    }
