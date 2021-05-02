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

package com.ze.stagybee.extractor

import com.ze.stagybee.extractor.routes.Status
import com.ze.stagybee.extractor.routes.Success
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.FlowPreview
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

@FlowPreview
@KtorExperimentalAPI
class StagyBeeExtractorTest {

    @Test
    fun testMeta() = withTestApplication({ main() }) {
        with(handleRequest(HttpMethod.Get, "/api/meta")) {
            val content = File("meta.json").readText()
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals(content, response.content)
        }
    }

    @Test
    fun testSubscribeEmptyBody() = withTestApplication({ main() }) {
        with(handleRequest(HttpMethod.Post, "/api/subscribe") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun testSubscribe() = withTestApplication({
        (environment.config as MapApplicationConfig).apply {
            put("ktor.environment", "dev")
        }
        main()
    }) {
        with(handleRequest(HttpMethod.Post, "/api/subscribe") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation",
                  "username": "username",
                  "password": "password",
                  "url": "https://localhost/receiver"
                }"""
            )
        }) {
            assertEquals(HttpStatusCode.OK, response.status())
            val message: Success = Json.decodeFromString(response.content ?: "")
            with(handleRequest(HttpMethod.Delete, "/api/unsubscribe/${message.sessionId}")) {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun testSubscribeMultiple() = withTestApplication({
        (environment.config as MapApplicationConfig).apply {
            put("ktor.environment", "dev")
        }
        main()
    }) {
        var sessionId0: String
        var sessionId1: String
        with(handleRequest(HttpMethod.Post, "/api/subscribe") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation",
                  "username": "username",
                  "password": "password",
                  "url": "https://localhost/receiver"
                }"""
            )
        }) {
            assertEquals(HttpStatusCode.OK, response.status())
            val message: Success = Json.decodeFromString(response.content ?: "")
            sessionId0 = message.sessionId
        }

        with(handleRequest(HttpMethod.Post, "/api/subscribe") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation2",
                  "username": "username",
                  "password": "password",
                  "url": "https://localhost/receiver2"
                }"""
            )
        }) {
            assertEquals(HttpStatusCode.OK, response.status())
            val message: Success = Json.decodeFromString(response.content ?: "")
            sessionId1 = message.sessionId
        }

        with(handleRequest(HttpMethod.Delete, "/api/unsubscribe/${sessionId1}")) {
            assertEquals(HttpStatusCode.OK, response.status())
        }

        with(handleRequest(HttpMethod.Delete, "/api/unsubscribe/abc")) {
            assertEquals(HttpStatusCode.NotFound, response.status())
        }

        with(handleRequest(HttpMethod.Delete, "/api/unsubscribe/${sessionId0}")) {
            assertEquals(HttpStatusCode.OK, response.status())
        }
    }

    @Test
    fun testStatus() = withTestApplication({
        (environment.config as MapApplicationConfig).apply {
            put("ktor.environment", "dev")
        }
        main()
    }) {
        var sessionId0: String
        with(handleRequest(HttpMethod.Post, "/api/subscribe") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation",
                  "username": "username",
                  "password": "password",
                  "url": "https://localhost/receiver"
                }"""
            )
        }) {
            assertEquals(HttpStatusCode.OK, response.status())
            val message: Success = Json.decodeFromString(response.content ?: "")
            assertEquals(true, message.success)
            sessionId0 = message.sessionId
        }
        with(handleRequest(HttpMethod.Get, "/api/status/${sessionId0}")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val message: Status = Json.decodeFromString(response.content ?: "")
            assertEquals(true, message.running)
        }
        with(handleRequest(HttpMethod.Delete, "/api/unsubscribe/${sessionId0}")) {
            assertEquals(HttpStatusCode.OK, response.status())
        }
        with(handleRequest(HttpMethod.Get, "/api/status/${sessionId0}")) {
            assertEquals(HttpStatusCode.NotFound, response.status())
            val message: Status = Json.decodeFromString(response.content ?: "")
            assertEquals(false, message.running)
        }
    }
}