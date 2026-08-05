package com.example.sample

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.sample", appContext.packageName)
    }

    @OptIn(FlowPreview::class)
    @Test
    fun testDebounce1() {
        runBlocking {
            println("begin")
            flow {
                emit(1)
                delay(100)
                emit(2)
                delay(100)
                emit(3)
                delay(501)
                emit(4)
                delay(409)
                emit(5)
            }.debounce(500)
                .collect {
                    println("collect $it")
                }
            println("end")
        }
    }

    @OptIn(FlowPreview::class)
    @Test
    fun testDebounce2() {
        runBlocking {
            println("begin")
            flow {
                emit(1)
                delay(100)
                emit(2)
                delay(100)
                // emit(3)
                // delay(1001)
                // emit(4)
            }.debounce(1000).collect {
                println("collect $it")
            }
            println("end")
        }
    }

    data class SearchQuery(val text: String, val priority: String)

    @OptIn(FlowPreview::class)
    @Test
    fun testDebounce3() {
        runBlocking {
            println("begin")
            flow {
                delay(49.milliseconds)
                emit(SearchQuery("aaa", "middle"))
                delay(49.milliseconds)
                emit(SearchQuery("Ko", "middle"))/*delay(101.milliseconds)
                emit(SearchQuery("URGENT: security", "high"))
                delay(99.milliseconds)
                emit(SearchQuery("Kotlin", "low"))
                delay(600.milliseconds)*/
            }.debounce { query ->
                when (query.priority) {
                    "low" -> {
                        println("10.milliseconds")
                        10.milliseconds
                    }

                    "middle" -> {
                        println("50.milliseconds")
                        50.milliseconds
                    }

                    "high" -> {
                        println("100.milliseconds")
                        100.milliseconds
                    }

                    else -> {
                        println("0.milliseconds")
                        0.milliseconds
                    }
                }
            }.collect { query ->
                println("Processing: ${query.text} [${query.priority}]")
            }
            println("end")
        }
    }

    sealed class FormField {
        data class Email(val value: String) : FormField()
        data class Password(val value: String) : FormField()
        data class Username(val value: String) : FormField()
    }

    @OptIn(FlowPreview::class)
    @Test
    fun testDebounce4() = runBlocking {
        println("Form Validation Example:")

        flow {
            emit(FormField.Email("a"))
            delay(100.milliseconds)
            emit(FormField.Email("ab"))
            delay(100.milliseconds)
            emit(FormField.Email("abc@example.com"))
            delay(50.milliseconds)

            emit(FormField.Password("pass"))
            delay(100.milliseconds)
            emit(FormField.Password("password123"))
            delay(600.milliseconds)

            emit(FormField.Username("user"))
            delay(700.milliseconds)
        }.debounce { field ->
            when (field) {
                is FormField.Email -> 500.milliseconds      // Email validation is expensive
                is FormField.Password -> 300.milliseconds   // Password strength check
                is FormField.Username -> 400.milliseconds   // Username availability check
            }
        }.collect { field ->
            when (field) {
                is FormField.Email -> println("Validating email: ${field.value}")
                is FormField.Password -> println("Checking password strength: ${field.value}")
                is FormField.Username -> println("Checking username availability: ${field.value}")
            }
        }
        //Checking password strength: password123
        //Checking username availability: user
    }

    @Test
    fun bufferTest() {
        runBlocking {
            val time = measureTimeMillis {
                (1..3).asFlow().onEach {
                    delay(100)
                }.buffer().collect {
                    delay(300)
                    println(it)
                }
            }
            println("Collected in $time ms")
        }
    }

    @Test
    fun conflateTest() {
        runBlocking {
            val time = measureTimeMillis {
                (1..3).asFlow().onEach {
                    delay(100)
                }.conflate().collect {
                    delay(300)
                    println(it)
                }
            }
            println("Collected in $time ms")
        }
    }

    @Test
    fun collectLatestTest() {
        runBlocking {
            val time = measureTimeMillis {
                (1..3).asFlow().onEach {
                    delay(100)
                }.collectLatest {
                    println("Collecting start $it")
                    delay(300)
                    println("Collecting end $it")
                }
            }
            println("Collected in $time ms")
        }
    }

    @Test
    fun zipTest() = runBlocking {
        val flow1 = flowOf("A", "B").onEach { delay(5000) }
        val flow2 = (1..3).asFlow().onEach { delay(100) }
        flow1.zip(flow2) { letter, number ->
            "$letter-$number"
        }.collect {
            println(it)
        }
    }

    @Test
    fun combineTest() = runBlocking {
        val flow1 = flowOf("A", "B").onEach { delay(150) }
        val flow2 = (1..3).asFlow().onEach { delay(100) }
        flow1.onStart {
            println("combine start")
        }.combine(flow2) { letter, number ->
            "$letter-$number"
        }.onCompletion {
            println("combine end $it")
        }.collect {
            println(it)
        }
    }


    @Test
    fun mergeTest() {
        runBlocking {
            val flow1 = flowOf("A", "B").onEach { delay(200) }
            val flow2 = (1..3).asFlow().onEach { delay(100) }
            val flow3 = flowOf("a").onEach { delay(300) }
            merge(flow1, flow2, flow3).collect {
                println(it)
            }
        }
    }

    @Test
    fun catchTest() {
        runBlocking {
            flow {
                emit(1)
                throw RuntimeException("Test Exception")
            }.catch {
                println("Caught exception: ${it.message}")
                emit(-1)
                // 不会捕获异常
                // throw RuntimeException("Rethrow Exception")
            }.collect {
                println(it)
            }
        }
    }

    @Test
    fun onCompletionTest() {
        runBlocking {
            flow {
                emit(1)
                emit(2)
                throw RuntimeException("Test Exception")
            }.onCompletion { cause ->
                if (cause != null) {
                    println("Flow completed with exception: ${cause.message}")
                } else {
                    println("Flow completed successfully")
                }
            }.collect {
                println(it)
            }
        }
    }

    @Test
    fun retryWhenTest() {
        runBlocking {
            var attemptCount = 0
            flow {
                emit(1)
                if (attemptCount < 2) {
                    attemptCount++
                    throw RuntimeException("Test Exception on attempt $attemptCount")
                }
                emit(2)
            }.retryWhen { cause, attempt ->
                // 没有异常不会执行到这里
                println("Attempt $attempt: Retrying due to ${cause.message}")
                delay(100)
                attempt < 3
            }.catch {
                println("Caught final error: ${it.message}")
            }.collect {
                println(it)
            }
        }
    }

    @Test
    fun onEachTest() {
        runBlocking {
            (1..3).asFlow().onEach {
                println("onEach: $it")
            }.collect {
                println("collect: $it")
            }
        }

    }

    @Test
    fun distinctUntilChangedTest() = runBlocking {
        flowOf(1, 1, 2, 2, 1, 3)
            .distinctUntilChanged()
            .collect { println(it) }
    }

}