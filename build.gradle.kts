/*
 * Copyright 2019-2020 Simon Zigelli
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.4.10"
    kotlin("plugin.serialization") version "1.4.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    application
}

group = "com.ze.stagybee.extractor"
version = "1.0.3"

repositories {
    jcenter()
    maven(url="https://dl.bintray.com/kotlin/ktor")
    maven(url="https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.zigellsn:webhookk:1.0.2")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.github.ajalt.clikt:clikt:3.0.1")
    implementation("io.ktor:ktor-client:1.4.2")
    implementation("io.ktor:ktor-client-cio:1.4.2")
    implementation("io.ktor:ktor-serialization:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("io.ktor:ktor-server-netty:1.4.2")

    testImplementation("junit:junit:4.13.1")
    testImplementation("io.ktor:ktor-server-tests:1.4.2")
    testImplementation("io.ktor:ktor-server-test-host:1.4.2")
}

application {
    mainClassName = "com.ze.stagybee.extractor.StagyBeeExtractorKt"
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("StagyBeeExtractor")
    archiveClassifier.set("")
    archiveVersion.set("")
}