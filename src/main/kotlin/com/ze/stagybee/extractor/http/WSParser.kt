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

package com.ze.stagybee.extractor.http

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

internal class WSParser {

    @Serializable
    sealed class Data {
        abstract val id: Int

        @Serializable
        @SerialName(addRow)
        data class AddRow(
            @Serializable(with = IntAsBooleanSerializer::class) val mutestatus: Boolean,
            @Serializable(with = IntAsBooleanSerializer::class) val speechrequest: Boolean,
            val name: String,
            val listener: Int,
            val connected: String,
            val sn: String,
            val utc_connected: String,
            val gn: String,
            val login: String,
            val type: Int,
            override val id: Int
        ) : Data()

        @Serializable
        @SerialName(delRow)
        data class DelRow(val listener: Int, override val id: Int) : Data()

        @Serializable
        @SerialName(speech)
        data class Speech(
            @Serializable(with = IntAsBooleanSerializer::class) val mutestatus: Boolean,
            @Serializable(with = IntAsBooleanSerializer::class) val speechrequest: Boolean,
            override val id: Int
        ) : Data()
    }

    @Serializable
    data class Action(
        val action: String,
        val data: Data
    )

    object IntAsBooleanSerializer : KSerializer<Boolean> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Boolean) =
            encoder.encodeInt(if (value) 1 else 0)

        override fun deserialize(decoder: Decoder): Boolean =
            decoder.decodeInt() == 1
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun parseMessage(message: String): Action? {
        val module = Json {
            classDiscriminator = "classType"
        }
        val a = message.indexOf("{", 1)
        val type = when {
            message.contains(addRow) -> addRow
            message.contains(delRow) -> delRow
            message.contains(unmute) -> speech
            message.contains(newSpeech) -> speech
            message.contains(delSpeech) -> speech
            else -> return null
        }
        val jsonString =
            message.subSequence(0..a)
                .toString() + """ "classType": "$type", """ + message.subSequence(a + 1 until message.length)
        return module.decodeFromString(jsonString)
    }

    companion object {
        const val addRow = "addrow"
        const val delRow = "delrow"
        const val unmute = "unmute"
        const val newSpeech = "newspeech"
        const val delSpeech = "delspeech"
        const val speech = "speech"
    }
}