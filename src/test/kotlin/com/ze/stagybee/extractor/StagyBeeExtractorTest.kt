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

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

@FlowPreview
@ExperimentalCoroutinesApi
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
    fun testSubscribe() = withTestApplication({ main() }) {
        with(handleRequest(HttpMethod.Post, "/api/subscribe") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{
                  "congregation": "Congregation",
                  "username": "username",
                  "password": "password",
                  "url": "http://localhost/reveiver"
                }"""
            )
        }) {
            assertEquals(HttpStatusCode.OK, response.status())
            // assertEquals(content, response.content)
        }
    }
}