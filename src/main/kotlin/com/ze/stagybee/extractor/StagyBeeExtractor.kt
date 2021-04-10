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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.zigellsn.webhookk.WebhookK
import com.ze.stagybee.extractor.routes.meta
import com.ze.stagybee.extractor.routes.status
import com.ze.stagybee.extractor.routes.subscribe
import com.ze.stagybee.extractor.routes.unsubscribe
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.http
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.serialization.*
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.security.Security

lateinit var webhooks: WebhookK

class Proxy : CliktCommand() {

    private val proxyUrl: String? by option(help = "Proxy")
    private val serverPort: Int by option(help = "Port").int().default(8080)

    @FlowPreview
    @ExperimentalCoroutinesApi
    @KtorExperimentalAPI
    override fun run() {
        System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
        Security.setProperty("securerandom.drbg.config", "HMAC_DRBG,SHA-512,256,pr_and_reseed")
        val env = applicationEngineEnvironment {
            module {
                main(proxyUrl)
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

        embeddedServer(Netty, env).start(true)
    }
}

fun main(args: Array<String>) = Proxy().main(args)

@ExperimentalCoroutinesApi
@FlowPreview
@KtorExperimentalAPI
fun Application.main(proxyUrl: String? = null, dispatcher: CoroutineDispatcher = Dispatchers.Default) {

    webhooks = WebhookK(HttpClient(CIO) {
        engine {
            proxy = if (!proxyUrl.isNullOrEmpty())
                ProxyBuilder.http(proxyUrl)
            else
                ProxyConfig.NO_PROXY
        }
    })

    webhookScope.launch(dispatcher) {
        webhooks.responses().collect { (id, res) ->
            applicationEngineEnvironment {
                log.trace("Status($id): $res")
            }
        }
    }

    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        json()
    }
    routing {
        route("/api") {
            subscribe()
            unsubscribe()
            status()
            meta()
        }
    }
}

private val webhookJob = SupervisorJob()
private val webhookScope = CoroutineScope(webhookJob)