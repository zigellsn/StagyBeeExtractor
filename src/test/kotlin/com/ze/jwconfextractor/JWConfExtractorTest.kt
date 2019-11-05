package com.ze.jwconfextractor

import io.ktor.application.Application
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class JWConfExtractorTest {

    @Test
    @KtorExperimentalAPI
    @ExperimentalCoroutinesApi
    fun testMeta() = withTestApplication(Application::main) {
        with(handleRequest(HttpMethod.Get, "/api/meta/")) {
            val content = File("meta.json").readText()
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals(content, response.content)
        }
    }

    @Test
    @KtorExperimentalAPI
    @ExperimentalCoroutinesApi
    fun testSubscribeEmptyBody() = withTestApplication(Application::main) {
        with(handleRequest(HttpMethod.Post, "/api/subscribe/") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    @KtorExperimentalAPI
    @ExperimentalCoroutinesApi
    fun testSubscribe() = withTestApplication(Application::main) {
        with(handleRequest(HttpMethod.Post, "/api/subscribe/") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            // setBody(Subscribe())
        }) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
            // assertEquals(content, response.content)
        }
    }

}