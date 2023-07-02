/*
 * Copyright 2019-2023 Simon Zigelli
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
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class StagyBeeExtractorTest {

    @Test
    fun testMeta() = testApplication {
        application {
            main()
        }
        environment {
            config = MapApplicationConfig("ktor.environment" to "dev")
        }
        val response = client.get("/api/meta")
        val content = File("meta.json").readText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(content, response.bodyAsText())
    }

    @Test
    fun testSubscribeEmptyBody() = testApplication {
        application {
            main()
        }
        environment {
            config = MapApplicationConfig("ktor.environment" to "dev")
        }
        val client = createClient {
            expectSuccess = false
        }
        val response = client.post("/api/subscribe") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testSubscribe() = testApplication {
        application {
            main()
        }
        environment {
            config = MapApplicationConfig("ktor.environment" to "dev")
        }
        val client = createClient {
            expectSuccess = false
        }
        var response = client.post("/api/subscribe") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation",
                  "username": "username",
                  "password": "password",
                  "url": "https://localhost/receiver"
                }"""
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val message: Success = Json.decodeFromString(response.bodyAsText())

        response = client.delete("/api/unsubscribe/${message.sessionId}") {
            assertEquals(HttpStatusCode.OK, response.status)
        }.body()
    }

    @Test
    fun testSubscribeMultiple() = testApplication {
        application {
            main()
        }
        environment {
            config = MapApplicationConfig("ktor.environment" to "dev")
        }
        val client = createClient {
            expectSuccess = false
        }
        val sessionId0: String
        val sessionId1: String
        var response = client.post("/api/subscribe") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation",
                  "username": "username",
                  "password": "password",
                  "url": "https://localhost/receiver"
                }"""
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        var message: Success = Json.decodeFromString(response.bodyAsText())
        sessionId0 = message.sessionId

        response = client.post("/api/subscribe") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation2",
                  "username": "username",
                  "password": "password",
                  "url": "https://localhost/receiver2"
                }"""
            )
        }.body()
        assertEquals(HttpStatusCode.OK, response.status)
        message = Json.decodeFromString(response.bodyAsText())
        sessionId1 = message.sessionId

        assertNotEquals(sessionId0, sessionId1)

        response = client.delete("/api/unsubscribe/${sessionId1}")
        assertEquals(HttpStatusCode.OK, response.status)

        response = client.delete("/api/unsubscribe/abc")
        assertEquals(HttpStatusCode.NotFound, response.status)

        response = client.delete("/api/unsubscribe/${sessionId0}")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testStatus() = testApplication {
        application {
            main()
        }
        environment {
            config = MapApplicationConfig("ktor.environment" to "dev")
        }
        val client = createClient {
            expectSuccess = false
        }

        val sessionId0: String

        var response = client.post("/api/subscribe") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation",
                  "username": "username",
                  "password": "password",
                  "url": "https://localhost/receiver"
                }"""
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val message: Success = Json.decodeFromString(response.bodyAsText())
        assertEquals(true, message.success)
        sessionId0 = message.sessionId
        assertNotEquals("", sessionId0)

        response = client.get("/api/status/${sessionId0}")
        assertEquals(HttpStatusCode.OK, response.status)
        var statusMessage: Status = Json.decodeFromString(response.bodyAsText())
        assertEquals(true, statusMessage.running)

        response = client.delete("/api/unsubscribe/${sessionId0}")
        assertEquals(HttpStatusCode.OK, response.status)

        response = client.get("/api/status/${sessionId0}")
        assertEquals(HttpStatusCode.NotFound, response.status)
        statusMessage = Json.decodeFromString(response.bodyAsText())
        assertEquals(false, statusMessage.running)
    }
}