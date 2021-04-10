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

package com.ze.stagybee.extractor.routes

import com.ze.stagybee.extractor.Extractor
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

typealias CongregationId = String
typealias SessionId = String

@Serializable
data class Subscribe(
    val url: String
) {
    var id: CongregationId = ""
    var congregation: String = ""
    var username: String = ""
    var password: String = ""
    var timeout: Long = -1L
}

@Serializable
data class Success(val success: Boolean) {
    var message: String = ""
    var sessionId: String = ""
}

@Serializable
data class Status(val running: Boolean = false) {
    var since: @Serializable(with = DateAsStringSerializer::class) LocalDateTime? = null
    var remaining: Long = -1L
    var timeout: Long = -1L
    var serverTime: @Serializable(with = DateAsStringSerializer::class) LocalDateTime = LocalDateTime.now()
}

@Serializable
data class ExtractorSession(
    val extractor: Extractor,
    val timeoutSpan: Long
) {
    private val timeoutTime = System.currentTimeMillis() + timeoutSpan
    val since: @Serializable(with = DateAsStringSerializer::class) LocalDateTime = LocalDateTime.now()
    val remaining
        get() = if (job != null) timeoutTime - System.currentTimeMillis() else timeoutSpan
    var job: Job? = null
    val listeners: MutableMap<SessionId, @Serializable(with = UrlAsStringSerializer::class) Url> = mutableMapOf()
}

typealias ExtractorSessions = MutableMap<CongregationId, ExtractorSession>

private object UrlAsStringSerializer : KSerializer<Url> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Url", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Url) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Url =
        Url(decoder.decodeString())
}

private object DateAsStringSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Url", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDateTime =
        decoder.decodeString().toDateTime(DateTimeFormatter.ISO_LOCAL_DATE_TIME, false)
}

fun String.toDateTime(
    formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    offset: Boolean = true
): LocalDateTime {
    return LocalDateTime.from(
        if (offset)
            formatter.parse(this, OffsetDateTime::from)
        else
            formatter.parse(this)
    )
}