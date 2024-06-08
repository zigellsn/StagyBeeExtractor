/*
 * Copyright 2019-2024 Simon Zigelli
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

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class WSParserTest {

    @Test
    fun parseMessage() = runTest {
        val cut = WSParser()
        var action = cut.parseMessage(
            """
            {"action": "addrow", "data": {"mutestatus": 0, "speechrequest": 0, "name": "Testy Test", "listener": 1, "connected": "2020-02-08 11:46:20", "sn": "Test", "utc_connected": "2020-02-08T10:46:20+0000", "gn": "Testy", "login": "00:13:14", "type": 3, "id": 123}}
        """.trimIndent()
        )
        assertNotNull(action)
        assert(action.data is WSParser.Data.AddRow)

        action = cut.parseMessage(
            """
            {"action": "delrow", "data": {"listener": 1, "id": 123}}
        """.trimIndent()
        )
        assertNotNull(action)
        assert(action.data is WSParser.Data.DelRow)

        action = cut.parseMessage(
            """
            {"action": "unmute", "data": {"mutestatus": 1, "speechrequest": 0, "id": 123}}
        """.trimIndent()
        )
        assertNotNull(action)
        assert(action.data is WSParser.Data.Speech)

        action = cut.parseMessage(
            """
            {"action": "newspeech", "data": {"mutestatus": 1, "speechrequest": 1, "id": 123}}
        """.trimIndent()
        )
        assertNotNull(action)
        assert(action.data is WSParser.Data.Speech)

        action = cut.parseMessage(
            """
            {"action": "delspeech", "data": {"mutestatus": 0, "speechrequest": 0, "id": 123}}
        """.trimIndent()
        )
        assertNotNull(action)
        assert(action.data is WSParser.Data.Speech)

        action = cut.parseMessage(
            """
            {"action": "ping"}
        """.trimIndent()
        )
        assertNull(action)

        action = cut.parseMessage(
            """
            {"action": "streamer", "data": {"date": "0", "utc_date": "0", "online": "0"}}
        """.trimIndent()
        )
        assertNull(action)

        action = cut.parseMessage(
            """
            {"actionn": "test"}
        """.trimIndent()
        )
        assertNull(action)

        action = cut.parseMessage(
            """
            {}
        """.trimIndent()
        )
        assertNull(action)
    }
}