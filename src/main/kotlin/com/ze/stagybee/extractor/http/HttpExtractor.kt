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

package com.ze.stagybee.extractor.http

import com.ze.stagybee.extractor.Extractor
import com.ze.stagybee.extractor.Name
import com.ze.stagybee.extractor.Names
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.cookies.AcceptAllCookiesStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow

open class HttpExtractor(
    private val id: String? = "",
    private val congregation: String? = "",
    private val username: String? = "",
    private val password: String? = ""
) : Extractor {

    @KtorExperimentalAPI
    private val client = HttpClient(CIO) {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(WebSockets)
        expectSuccess = false
    }
    private val parser = WSParser()
    private val names = mutableListOf<Name>()

    @KtorExperimentalAPI
    override suspend fun stopListener() {
        client.get<String>(urlLogout)
        client.close()
    }

    override suspend fun getListenersSnapshot(): Names = Names(names)

    @ExperimentalCoroutinesApi
    @FlowPreview
    @KtorExperimentalAPI
    @InternalCoroutinesApi
    override suspend fun getListeners(block: suspend (Names) -> Unit) {
        login()
        client.ws(
            method = HttpMethod.Get,
            host = webSocketUrl,
            port = webSocketPort,
            path = webSocketPath
        ) {
            incoming.consumeAsFlow().collect { frame ->
                when (frame) {
                    is Frame.Text -> {
                        processMessages(frame.readText())
                        block(Names(names))
                    }
                }
            }
        }
    }

    @KtorExperimentalAPI
    private suspend fun login() {
        if (this.id != null && this.id.length == 12) {
            client.get<String>("$urlAutoLogin${this.id}")
        } else {
            val myBody =
                "loginstatus=auth&congregation=${this.congregation}&congregation_id=&username=${this.username}&password=${this.password}"
            client.post<String> {
                url(sUrl)
                body = myBody
                headers {
                    append("Content-Type", "application/x-www-form-urlencoded")
                }
            }
        }
    }

    private fun processMessages(message: String) {
        val action = parser.parseMessage(message)
        when (action?.data) {
            is WSParser.Data.AddRow -> {
                val row = action.data as WSParser.Data.AddRow
                names.add(Name(row.id, row.sn, row.gn, row.speechrequest, row.mutestatus).also {
                    it.listenerCount = row.listener
                })
            }
            is WSParser.Data.DelRow -> {
                val row = names.find { it.id == action.data.id } ?: return
                names.remove(row)
            }
            is WSParser.Data.Speech -> {
                val row = action.data as WSParser.Data.Speech
                names[names.indexOf(names.find { it.id == action.data.id })].requestToSpeak = row.speechrequest
                names[names.indexOf(names.find { it.id == action.data.id })].speaking = row.mutestatus
            }
        }
    }

    companion object {
        const val urlAutoLogin = "https://jwconf.org/stage.php?key="
        const val sUrl = "https://jwconf.org/login.php"
        const val urlLogout = "https://jwconf.org/index.php?logout"
        const val webSocketUrl = "jwconf.org"
        const val webSocketPath = "/websocket"
        const val webSocketPort = 80
    }
}