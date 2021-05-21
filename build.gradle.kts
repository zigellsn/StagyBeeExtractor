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

val kotlinVersion by extra("1.5.0")
val ktorVersion by extra("1.5.4")

plugins {
    kotlin("jvm") version "1.5.0"
    kotlin("plugin.serialization") version "1.5.0"
    application
}

group = "com.ze.stagybee.extractor"
version = "1.0.10"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("com.github.zigellsn:webhookk:1.1.0-rc01")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("io.ktor:ktor-client:${ktorVersion}")
    implementation("io.ktor:ktor-client-cio:${ktorVersion}")
    implementation("io.ktor:ktor-serialization:${ktorVersion}")
    implementation("io.ktor:ktor-network-tls-certificates:${ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${ktorVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.ktor:ktor-server-tests:${ktorVersion}")
    testImplementation("io.ktor:ktor-server-test-host:${ktorVersion}")
}

application {
    mainClass.set("com.ze.stagybee.extractor.StagyBeeExtractorKt")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}
