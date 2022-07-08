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

package com.ze.stagybee.extractor.http

import com.ze.stagybee.extractor.Extractor
import com.ze.stagybee.extractor.Name
import com.ze.stagybee.extractor.Names
import com.ze.stagybee.extractor.routes.LoginRequest
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.isActive

open class HttpExtractor(
    private val id: String? = "",
    private val congregation: String? = "",
    private val username: String? = "",
    private val password: String? = ""
) : Extractor {

    class MyCookiesStorage(private val cookiesStorage: CookiesStorage) : CookiesStorage by cookiesStorage {
        override suspend fun get(requestUrl: Url): List<Cookie> =
            cookiesStorage.get(requestUrl).map { it.copy(encoding = CookieEncoding.RAW) }
    }

    private val client = HttpClient(CIO) {
        BrowserUserAgent()
        install(HttpCookies) {
            storage = MyCookiesStorage(AcceptAllCookiesStorage())
        }
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets)
        expectSuccess = false
    }

    private val parser = WSParser()
    private val names = mutableListOf<Name>()

    override suspend fun stopListener() {
        if (client.isActive)
            client.close()
    }

    override suspend fun getListenersSnapshot(): Names = Names(names)

    override suspend fun getListeners(token: String, block: suspend (Names) -> Unit) {
        try {
            client.wss(
                method = HttpMethod.Get,
                host = webSocketUrl,
                port = webSocketPort,
                path = webSocketPath,
                request = {
                    val cookie = Cookie(
                        encoding = CookieEncoding.RAW,
                        name = "X-Authorization",
                        value = token,
                        path = "/",
                        domain = webSocketUrl
                    )
                    val renderedCookie = renderCookieHeader(cookie)
                    headers {
                        append(HttpHeaders.Cookie, renderedCookie)
                    }
                }
            ) {
                try {
                    incoming.consumeAsFlow().filterIsInstance<Frame.Text>().collect { frame ->
                        processMessages(frame.readText())
                        block(Names(names))
                    }
                } catch (e: ClosedReceiveChannelException) {
                    print(closeReason.await())
                } catch (e: Throwable) {
                    print(closeReason.await())
                    applicationEngineEnvironment {
                        log.trace(e.toString())
                    }
                }
            }
        } catch (e: Exception) {
            applicationEngineEnvironment {
                log.trace(e.toString())
            }
        }
    }

    override suspend fun login(): HttpResponse? {
        val myBody = if (this.id != null && this.id.length == 12) {
            LoginRequest(key = this.id)
        } else {
            LoginRequest(
                congregation = this.congregation ?: "",
                username = this.username ?: "",
                password = this.password ?: ""
            )
        }
        return client.post(urlLogin) {
            contentType(ContentType.Application.Json)
            setBody(myBody)
        }
    }

    private fun processMessages(message: String) {
        val action = parser.parseMessage(message)
        when (action?.data) {
            is WSParser.Data.AddRow -> {
                val row = action.data as WSParser.Data.AddRow
                names.add(
                    Name(
                        row.id,
                        row.sn,
                        row.gn,
                        row.speechrequest,
                        row.mutestatus,
                        listenerType = row.type
                    ).also {
                        it.listenerCount = row.listener
                    })
            }
            is WSParser.Data.DelRow -> {
                val row = names.find { it.id == (action.data as WSParser.Data.DelRow).id } ?: return
                names.remove(row)
            }
            is WSParser.Data.Speech -> {
                val row = action.data as WSParser.Data.Speech
                names[names.indexOf(names.find { it.id == action.data.id })].requestToSpeak = row.speechrequest
                names[names.indexOf(names.find { it.id == action.data.id })].speaking = row.mutestatus
            }
            null -> {}
        }
    }

    companion object {
        const val urlLogin = "https://jwconf.org/api/v1.0/login"
        const val webSocketUrl = "jwconf.org"
        const val webSocketPath = "/websocket"
        const val webSocketPort = 443
    }
}