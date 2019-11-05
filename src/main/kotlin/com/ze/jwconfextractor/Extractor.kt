package com.ze.jwconfextractor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.openqa.selenium.By
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.lang.System.currentTimeMillis
import java.time.LocalDateTime

data class ExtractorStatus(
    val running: Boolean,
    val since: LocalDateTime,
    val remaining: Long,
    val timeout: Long
)

data class Name(val familyName: String, val givenName: String) {
    var listenerCount: Int = 0
    var requestToSpeak: Boolean = false
    var speaking: Boolean = false
}

data class Names(val names: List<Name>)

interface Extractor {
    fun getListenersSnapshot(): Names?
    suspend fun getListeners(): Flow<Any>
    fun stopListener()
    val status: ExtractorStatus
}

open class WebExtractor(
    private val id: String? = "",
    private val congregation: String? = "",
    private val username: String? = "",
    private val password: String? = "",
    private val frequency: Long = 1000L,
    private val timeout: Long = 1080000L
) : Extractor {

    override fun stopListener() {
        isActive = false
    }

    init {
        System.setProperty("webdriver.chrome.driver", "C:\\driver\\chromedriver.exe")
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
    protected var t0 = currentTimeMillis() + timeout
    private lateinit var driver: ChromeDriver

    protected var isActive: Boolean = false
    protected var since = LocalDateTime.now()!!
    private val remaining
        get() = if (isActive) t0 - currentTimeMillis() else timeout
    override val status: ExtractorStatus
        get() = ExtractorStatus(isActive, since, remaining, timeout)

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
                val name = Name(
                    it.findElements(By.className(this.classLastName))[0].getAttribute("textContent"),
                    it.findElements(By.className(classFirstName))[0].getAttribute("textContent")
                )
                name.listenerCount =
                    it.findElements(By.className(this.classListenerCount))[0].getAttribute("textContent").toInt()
                val className = it.getAttribute("class")
                name.requestToSpeak = className.contains(classRequestToSpeak)
                name.speaking = className.contains(classSpeaking)
                names.add(name)
            } catch (e: Exception) {

            }
        }
        return Names(names)
    }

    override fun getListenersSnapshot(): Names? {
        return getNames()
    }

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    override suspend fun getListeners() = flow {
        isActive = true
        val options = ChromeOptions().addArguments("--headless")
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL)
        driver = ChromeDriver(options)
        login()
        val wait = WebDriverWait(driver, internalTimeout)
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(idNames)))
        t0 = currentTimeMillis() + timeout
        since = LocalDateTime.now()!!
        emit(isActive)
        var previousNames: Names? = null
        while (currentTimeMillis() < t0 && isActive) {
            val names = getNames()
            if (names != previousNames) {
                previousNames = names
                emit(names)
            }
            delay(frequency)
        }
        this.emit(isActive)
        logoff()
    }
}