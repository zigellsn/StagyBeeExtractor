/*
 * Copyright 2019 Simon Zigelli
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

import org.openqa.selenium.By
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait

open class JWConfExtractor(
    private val id: String? = "",
    private val congregation: String? = "",
    private val username: String? = "",
    private val password: String? = "",
    frequency: Long = 1000L,
    override val timeout: Long = 1080000L,
    driverBin: String?
) : WebExtractor(frequency, timeout) {

    init {
        if (driverBin != null)
            System.setProperty("webdriver.chrome.driver", driverBin)
    }

    private lateinit var driver: WebDriver

    override fun login() {
        if (id != null && id.length == 12) {
            driver.get("$urlAutoLogin${id}")
        } else {
            driver.get(url)
            driver.findElement(By.name(idCongregation)).also {
                it.sendKeys(this.congregation)
            }
            driver.findElement(By.name(idUsername)).also {
                it.sendKeys(this.username)
            }
            driver.findElement(By.name(idPassword)).also {
                it.sendKeys(this.password)
            }
            driver.findElement(By.id(loginTag)).also {
                it.click()
            }
        }
    }

    override fun logoff() {
        driver.get(urlLogout)
        driver.quit()
    }

    override suspend fun getNames(): Names {
        val elements = driver.findElements(By.className(className))
        val names = mutableListOf<Name>()
        elements.forEach {
            try {
                val name = Name(
                    it.findElements(By.className(classLastName))[0].getAttribute(
                        "textContent"
                    ),
                    it.findElements(By.className(classFirstName))[0].getAttribute(
                        "textContent"
                    )
                )
                name.listenerCount =
                    it.findElements(By.className(classListenerCount))[0].getAttribute("textContent").toInt()
                val className = it.getAttribute("class")
                name.requestToSpeak = className.contains(classRequestToSpeak)
                name.speaking = className.contains(classSpeaking)
                names.add(name)
            } catch (e: Exception) {

            }
        }
        return Names(names)
    }

    /*     @ExperimentalCoroutinesApi
    fun CoroutineScope.getChannel(): ReceiveChannel<Any> = produce {
        initExtractor()
        var previousNames: Names? = null
        isActive = true
        send(isActive)
        while (System.currentTimeMillis() < t0 && isActive) {
            val names = getNames()
            if (names != previousNames) {
                previousNames = names
                send(names)
            }
            delay(frequency)
        }
        send(isActive)
        shutdownExtractor()
    } */

    override fun initDriver() {
        val options = ChromeOptions().addArguments("--headless")
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL)
        driver = ChromeDriver(options)
        login()
        val wait = WebDriverWait(
            driver,
            internalTimeout
        )
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(idNames)))
    }

    companion object {
        const val idCongregation = "congregation"
        const val urlAutoLogin = "https://jwconf.org/?key="
        const val url = "https://jwconf.org/login.php?source=index.php&"
        const val urlLogout = "https://jwconf.org/index.php?logout"
        const val internalTimeout = 30000L
        const val idUsername = "username"
        const val idPassword = "password"
        const val idNames = "names"
        const val loginTag = "login"
        const val className = "name"
        const val classFirstName = "firstName"
        const val classLastName = "lastName"
        const val classListenerCount = "listenercount"
        const val classRequestToSpeak = "meldung"
        const val classSpeaking = "meldungActive"
    }
}