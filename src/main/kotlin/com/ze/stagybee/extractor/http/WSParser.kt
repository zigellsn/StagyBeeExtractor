/*
 * Copyright 2020 Simon Zigelli
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


import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

internal class WSParser {

    private val mapper = jacksonObjectMapper()

    sealed class Data(open val id: Int) {
        @JsonTypeName("addrow")
        data class AddRow(
            val mutestatus: Boolean, val speechrequest: Boolean, val name: String,
            val listener: Int, val connected: String, val sn: String, val utc_connected: String,
            val gn: String, val login: String, val type: Int, override val id: Int
        ) : Data(id)

        data class DelRow(val listener: Int, override val id: Int) : Data(id)
        data class Speech(val mutestatus: Boolean, val speechrequest: Boolean, override val id: Int) : Data(id)
    }

    data class Action(val action: String,
                      @JsonTypeInfo(property = "action", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, use = JsonTypeInfo.Id.NAME)
                      @JsonSubTypes(
                          JsonSubTypes.Type(value = Data.AddRow::class, name = "addrow"),
                          JsonSubTypes.Type(value = Data.DelRow::class, name = "delrow"),
                          JsonSubTypes.Type(value = Data.Speech::class, name = "unmute"),
                          JsonSubTypes.Type(value = Data.Speech::class, name = "newspeech"),
                          JsonSubTypes.Type(value = Data.Speech::class, name = "delspeech")
                      )
                      val data: Data
    )

    fun parseMessage(message: String): Action? {
        return try {
            mapper.readValue(message)
        } catch (e: MismatchedInputException) {
            null
        } catch (e: MissingKotlinParameterException) {
            null
        }
    }
}