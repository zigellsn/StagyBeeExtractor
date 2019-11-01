package com.ze.jwconfextractor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.openqa.selenium.By
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.lang.Exception
import java.lang.System.currentTimeMillis
import java.time.LocalDateTime

data class Name(
    val familyName: String,
    val givenName: String,
    val listenerCount: Int,
    val requestToSpeak: Boolean,
    val speaking: Boolean
)

data class Names(val names: List<Name>)

class Extractor(
    private val id: String? = "",
    private val congregation: String? = "",
    private val username: String? = "",
    private val password: String? = "",
    private val frequency: Long = 1000L,
    val timeout: Long = 1080000L
) {

    init {
        System.setProperty("webdriver.chrome.driver", "C:\\temp\\chromedriver.exe")
    }

    private val idCongregation = "congregation"
    private val urlAutoLogin = "https://jwconf.org/?key="
    private val url = "https://jwconf.org/login.php?source=index.php&"
    private val urlLogout = "https://jwconf.org/index.php?logout"
    private val internalTimeout = 30000L
    private val idUsername = "username"
    private val idPassword = "password"
    private val idNames = "names"
    private val loginTag = "login"
    private val className = "name"
    private val classFirstName = "firstName"
    private val classLastName = "lastName"
    private val classListenerCount = "listenercount"
    private val classRequestToSpeak = "meldung"
    private val classSpeaking = "meldungActive"
    private var t0 = currentTimeMillis() + timeout
    private lateinit var driver: ChromeDriver

    var running = false
    var since = LocalDateTime.now()!!
    fun remaining() = if (running) t0 - currentTimeMillis() else timeout

    private fun login() {
        if (id != null && id.length == 12) {
            driver.get("${urlAutoLogin}${id}")
        } else {
            driver.get(url)
            driver.findElement(By.name(this.idCongregation)).also {
                it.sendKeys(this.congregation)
            }
            driver.findElement(By.name(this.idUsername)).also {
                it.sendKeys(this.username)
            }
            driver.findElement(By.name(this.idPassword)).also {
                it.sendKeys(this.password)
            }
            driver.findElement(By.id(this.loginTag)).also {
                it.click()
            }
        }
    }

    private fun logoff() {
        driver.get(urlLogout)
        driver.quit()
    }

    private fun getNames(): Names {
        val elements = driver.findElements(By.className(this.className))
        val names = mutableListOf<Name>()
        elements.forEach {
            try {
                val givenName = it.findElements(By.className(classFirstName))[0].getAttribute("textContent")
                val familyName = it.findElements(By.className(this.classLastName))[0].getAttribute("textContent")
                val listenerCount = it.findElements(By.className(this.classListenerCount))[0].getAttribute("textContent").toInt()
                val className = it.getAttribute("class")
                val requestToSpeak = className.contains(classRequestToSpeak)
                val speaking = className.contains(classSpeaking)
                names.add(Name(familyName, givenName, listenerCount, requestToSpeak, speaking))
            } catch (e: Exception) {

            }
        }
        return Names(names)
    }

    fun getListenersSnapshot(): Names? {
        if(running) {
            return getNames()
        }
        return null
    }

    suspend fun getListeners() = flow {
        val options = ChromeOptions().addArguments("--headless")
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL)
        driver = ChromeDriver(options)
        login()
        val wait = WebDriverWait(driver, internalTimeout)
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(idNames)))
        t0 = currentTimeMillis() + timeout
        since = LocalDateTime.now()!!
        running = true
        emit(running)
        var previousNames: Names? = null
        while (currentTimeMillis() < t0 && running) {
            val names = getNames()
            if (names != previousNames) {
                previousNames = names
                emit(names)
            }
            delay(frequency)
        }
        this.emit(running)
        logoff()
    }
}