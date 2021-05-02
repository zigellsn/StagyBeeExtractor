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

import com.github.zigellsn.webhookk.WebhookK
import com.ze.stagybee.extractor.routes.meta
import com.ze.stagybee.extractor.routes.status
import com.ze.stagybee.extractor.routes.subscribe
import com.ze.stagybee.extractor.routes.unsubscribe
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.features.*
import io.ktor.network.tls.certificates.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.security.Security

lateinit var webhooks: WebhookK

fun main(args: Array<String>) {
    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    Security.setProperty("securerandom.drbg.config", "HMAC_DRBG,SHA-512,256,pr_and_reseed")
    val file = File("build/temporary.jks")
    if (!file.exists()) {
        file.parentFile.mkdirs()
        generateCertificate(file)
    }
    embeddedServer(Netty, commandLineEnvironment(args)).start(true)
}

@FlowPreview
@KtorExperimentalAPI
fun Application.main(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    val proxyUrl = environment.config.propertyOrNull("ktor.proxy")?.getString()
    val env = environment.config.propertyOrNull("ktor.environment")?.getString()
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
            subscribe(env, webhookScope)
            unsubscribe()
            status()
            meta()
        }
    }
}

private val webhookJob = SupervisorJob()
private val webhookScope = CoroutineScope(webhookJob)