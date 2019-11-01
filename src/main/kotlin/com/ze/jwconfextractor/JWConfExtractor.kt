package com.ze.jwconfextractor

import com.ze.webhookk.WebhookK
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.GsonConverter
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.ContentTransformationException
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.routing.Routing
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.*


data class Subscribe(
    val url: String,
    val id: String?,
    val congregation: String?,
    val userName: String?,
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

const val xJWConfExtractorEvent = "X-JWCONFEXTRACTOR-EVENT"
const val xJWConfExtractorAction = "X-JWCONFEXTRACTOR-ACTION"
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

val webhooks = WebhookK(HttpClient(OkHttp))
val extractors = mutableMapOf<String, Extractor>()
val listeners = mutableMapOf<String, Pair<String, URI>>()

suspend fun startListener(extractor: Extractor, id: String) {
    extractor.getListeners().collect {
        if (it is Boolean) {
            webhooks.trigger(id, Status(it), listOf(xJWConfExtractorEvent to listOf(eventStatus)))
            if (it) {
                println("Running ID ${id}...")
            } else {
                println("Stopped ID ${id}...")
            }
        } else {
            webhooks.trigger(id, it, listOf(xJWConfExtractorEvent to listOf(eventListeners)))
        }
    }
    //TODO: Send error
}

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        register(ContentType.Application.Json, GsonConverter())
        gson {
            setDateFormat(DateFormat.LONG)
            setPrettyPrinting()
        }
    }
    install(Routing) {
        post("/api/subscribe/") {
            call.response.headers.append(xJWConfExtractorAction, actionSubscribe)
            val s = try {
                call.receive<Subscribe>()
            } catch (e: ContentTransformationException) {
                call.response.status(HttpStatusCode.BadRequest)
                call.respond(Success(false, "Empty request body"))
                return@post
            }
            if (s.url.isEmpty()) {
                call.response.status(HttpStatusCode.BadRequest)
                call.respond(Success(false, "URL must not be empty"))
                return@post
            }
            val md: MessageDigest = MessageDigest.getInstance("SHA")
            val topic = if (s.id.isNullOrEmpty() || s.id.length > 12) {
                md.digest("${s.congregation}:${s.userName}:${s.password}".toByteArray()).toString()
            } else {
                s.id
            }
            val uri = try {
                URI.create(s.url)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, Success(false, e.toString()))
                return@post
            }

            val sessionId = if (!listeners.containsValue(topic to uri)) {
                val newSessionId = UUID.randomUUID().toString()
                listeners[newSessionId] = topic to uri
                newSessionId
            } else {
                val sessions = listeners.filterValues { it == topic to uri }.keys
                if (sessions.size == 1) {
                    sessions.toList()[0]
                } else {
                    //TODO: Exception
                    throw Exception()
                }
            }

            if (!extractors.containsKey(topic)) {

                val timeout = if (s.timeout == null)
                    THREE_HOURS
                else {
                    if (s.timeout < QUARTER_HOUR) {
                        QUARTER_HOUR
                    }
                    s.timeout
                }

                try {
                    extractors[topic] = Extractor(topic, s.congregation, s.userName, s.password, FREQUENCY, timeout)
                } catch (err: Exception) {
                    call.response.headers.append(xJWConfExtractorEvent, eventError)
                    call.respond(HttpStatusCode.InternalServerError, Success(false, err.toString()))
                    return@post
                } finally {
                    GlobalScope.launch {
                        startListener(extractors[topic] ?: throw AssertionError("Set to null by another thread"), topic)
                    }
                }
            } else {
                webhooks.trigger(
                    topic,
                    extractors[topic]?.getListenersSnapshot() ?: Names(emptyList()),
                    listOf(xJWConfExtractorEvent to listOf(eventListeners))
                )
            }

            webhooks.add(topic, uri)
            call.respond(Success(true, sessionId = sessionId))
        }
        delete("/api/unsubscribe/{sessionId}/") {
            val sessionId = call.parameters["sessionId"]
            call.response.headers.append(xJWConfExtractorAction, actionUnsubscribe)

            if (!listeners.containsKey(sessionId)) {
                call.respond(Success(false, "Unknown session id"))
                return@delete
            }

            val session = listeners[sessionId] ?: throw AssertionError("")

            if (extractors.containsKey(session.first)) {
                webhooks.remove(session.first, session.second)
                if (webhooks.getUris(session.first).count() == 0) {
                    extractors[session.first]?.running = false
                    extractors.remove(session.first)
                    listeners.remove(session.first)
                }
            }
            call.respond(Success(true))
        }
        get("/api/status/{sessionId}/") {
            call.response.headers.append(xJWConfExtractorAction, actionStatus)
            val sessionId = call.parameters["sessionId"] ?: throw AssertionError("")
            if (!listeners.containsKey(sessionId)) {
                call.respond(Status(running = false))
                return@get
            }
            val extractor = extractors[listeners[sessionId]?.first]
            if (extractor != null) {
                call.respond(
                    Status(
                        extractor.running,
                        extractor.since,
                        extractor.remaining(),
                        extractor.timeout,
                        LocalDateTime.now()
                    )
                )
            } else
                call.respond(Status(false))
        }
        get("/api/meta/") {
            call.response.headers.append(xJWConfExtractorAction, actionMeta)
            call.respondFile(File("meta.json"))
        }
    }
}