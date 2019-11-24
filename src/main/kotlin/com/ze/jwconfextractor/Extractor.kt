package com.ze.jwconfextractor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.openqa.selenium.By
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.WebDriver
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

data class Name(
    val familyName: String, val givenName: String, var requestToSpeak: Boolean = false,
    var speaking: Boolean = false
) {
    var listenerCount: Int = 0
}

data class Names(val names: List<Name>)

interface Extractor {
    suspend fun getListenersSnapshot(): Names?
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
    protected val timeout: Long = 1080000L
) : Extractor {

    override fun stopListener() {
        isActive = false
    }

    init {
        System.setProperty("webdriver.chrome.driver", "C:\\temp\\chromedriver.exe")
    }

    protected var t0 = currentTimeMillis() + timeout
    protected var since = LocalDateTime.now()!!
    private lateinit var driver: WebDriver
    private var isActive: Boolean = false
    private val remaining
        get() = if (isActive) t0 - currentTimeMillis() else timeout
    override val status: ExtractorStatus
        get() = ExtractorStatus(isActive, since, remaining, timeout)

    private fun login() {
        if (id != null && id.length == 12) {
            driver.get("${urlAutoLogin}${id}")
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

    private fun logoff() {
        driver.get(urlLogout)
        driver.quit()
    }

    protected open suspend fun getNames(): Names {
        val elements = driver.findElements(By.className(className))
        val names = mutableListOf<Name>()
        elements.forEach {
            try {
                val name = Name(
                    it.findElements(By.className(classLastName))[0].getAttribute("textContent"),
                    it.findElements(By.className(classFirstName))[0].getAttribute("textContent")
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

    override suspend fun getListenersSnapshot(): Names? {
        return getNames()
    }

    @ExperimentalCoroutinesApi
    @InternalCoroutinesApi
    override suspend fun getListeners() = flow {
        initExtractor()
        var previousNames: Names? = null
        isActive = true
        emit(isActive)
        while (currentTimeMillis() < t0 && isActive) {
            val names = getNames()
            if (names != previousNames) {
                previousNames = names
                emit(names)
            }
            delay(frequency)
        }
        emit(isActive)
        shutdownExtractor()
    }

    @ExperimentalCoroutinesApi
    fun CoroutineScope.getChannel(): ReceiveChannel<Any> = produce {
        initExtractor()
        var previousNames: Names? = null
        isActive = true
        send(isActive)
        while (currentTimeMillis() < t0 && isActive) {
            val names = getNames()
            if (names != previousNames) {
                previousNames = names
                send(names)
            }
            delay(frequency)
        }
        send(isActive)
        shutdownExtractor()
    }

    protected open fun initExtractor() {
        t0 = currentTimeMillis() + timeout
        since = LocalDateTime.now()!!
        initDriver()
    }

    protected open fun shutdownExtractor() {
        logoff()
    }

    private fun initDriver() {
        val options = ChromeOptions().addArguments("--headless")
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL)
        driver = ChromeDriver(options)
        login()
        val wait = WebDriverWait(driver, internalTimeout)
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