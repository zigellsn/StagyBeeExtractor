package com.ze.jwconfextractor

import io.ktor.application.Application
import io.ktor.http.*
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class JWConfExtractorTest {

    @Test
    fun testMeta() = withTestApplication(Application::main) {
        with(handleRequest(HttpMethod.Get, "/api/meta/")) {
            val content = File("meta.json").readText()
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals(content, response.content)
        }
    }

    @Test
    fun testSubscribeEmptyBody() = withTestApplication(Application::main) {
        with(handleRequest(HttpMethod.Post, "/api/subscribe/")) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun testSubscribe() = withTestApplication(Application::main) {
        with(handleRequest(HttpMethod.Post, "/api/subscribe/") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            // setBody(Subscribe())
        }) {
            assertEquals(HttpStatusCode.OK, response.status())
            // assertEquals(content, response.content)
        }
    }

}