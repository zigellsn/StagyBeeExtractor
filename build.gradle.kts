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

plugins {
    kotlin("jvm") version "1.7.0"
    kotlin("plugin.serialization") version "1.7.0"
    application
}

group = "com.ze.stagybee.extractor"
version = "1.0.16"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation(libs.webhookk)
    implementation(libs.logback)
    implementation(libs.ktor.cio)
    implementation(libs.ktor.serialization.kotlinx)
    implementation(libs.ktor.client.contentnegotiation)
    implementation(libs.ktor.tls)
    implementation(libs.ktor.netty)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.defaultheaders)
    implementation(libs.ktor.server.calllogging)
    implementation(libs.ktor.server.contentnegotiation)

    testImplementation(libs.test.ktor.serverhost)
    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.kotlin.coroutine)
}

application {
    mainClass.set("com.ze.stagybee.extractor.StagyBeeExtractorKt")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        kotlinOptions.freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        kotlinOptions.freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}
