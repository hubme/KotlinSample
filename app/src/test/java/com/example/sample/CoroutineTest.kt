package com.example.sample

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CoroutineTest {
    @Test
    fun flowTest() = runBlocking {
        val result = flowOf(1, 3, 10)
            .fold(100){
                accumulator, element -> 110
            }
        println("result: $result")
    }

    @Test
    fun builderTest() = runBlocking {
        val job = launch(start = CoroutineStart.LAZY) {
            networkRequest()
            println("result received")
        }
        delay(200)
        job.start()
        println("end of runBlocking")

        /*
        父协程会等待所有子协程完成后，自身才算完成。
        output:
        end of runBlocking
        result received
         */
    }

    suspend fun networkRequest(): String {
        delay(500)
        return "Result"
    }

    @Test
    fun asyncTest() = runBlocking {
        val startTime = System.currentTimeMillis()

        // async 会立即启动一个新协程
        val deferred1 = async {
            val result = networkCall(1).also {
                println("result received: $it after ${elapsedMillis(startTime)}ms")
            }
            result
        }

        val deferred2 = async {
            val result2 = networkCall(2)
            println("result received: $result2 after ${elapsedMillis(startTime)}ms")
            result2
        }

        // await() 是一个挂起函数，它会等待 Deferred 的结果。如果结果尚未就绪，就挂起；如果已经完成，就直接返回。
        val resultList = listOf(deferred1.await(), deferred2.await())
        println("Result list: $resultList after ${elapsedMillis(startTime)}ms")

    }

    @Test
    fun asyncTest2() = runBlocking {
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            println("Caught $exception in CoroutineExceptionHandler")
        }

        CoroutineScope(Job() + exceptionHandler).async {
            val deferred = async {
                delay(200)
                // 异常被封装在 Deferred 中，直到 await() 才抛出
                throw RuntimeException()
            }
        }

        Thread.sleep(1000)
    }

    suspend fun networkCall(number: Int): String {
        delay(500)
        return "Result $number"
    }

    fun elapsedMillis(startTime: Long) = System.currentTimeMillis() - startTime

    @Test
    fun cancelTest1() = runBlocking {
        val job = launch(Dispatchers.Default) {
            repeat(10) { index ->
                if (isActive) {
                    println("operation number $index")
                    // 阻塞调用，不会自动响应取消，需要通过 isActive 手动检查
                    Thread.sleep(100)
                } else {
                    // perform some cleanup on cancellation
                    withContext(NonCancellable) {
                        delay(100)
                        println("Clean up done!")
                    }
                    throw kotlin.coroutines.cancellation.CancellationException()
                }
            }
        }

        delay(250)
        println("Cancelling Coroutine")
        job.cancel()

        val globalCoroutineJob = GlobalScope.launch {
            repeat(10) {
                println("$it")
                // 挂起函数，自动检查取消，不需要手动检查 isActive
                delay(100)
            }
        }
        delay(250)
        globalCoroutineJob.cancel()
        delay(1000)
    }

    @Test
    fun sequenceTest() = runBlocking {
        println("numbers: ")
        val numbers = generateSequence(1) { it + 1 }
        numbers.take(10).forEach {
            print("$it ")
        }

        val pairs = generateSequence(Pair(0, 1)) { Pair(it.second, it.first + it.second) }.map { it.first }

        println()
        println("pairs: ")
        pairs.take(10).forEach {
            print("$it ")
        }

        println()
        println("custom: ")
        val custom = sequence {
            yield(1)
            yieldAll(listOf(2, 3, 4))
            yield(5)
        }
        custom.forEach {
            print("$it ")
        }
        println()

        // Sequence:逐元素处理,按需求值,找到结果立即停止
        val result = sequenceOf(1, 2, 3, 4, 5).map {
            println("map $it");
            it * 2
        }.filter {
            println("filter $it");
            it > 4
        }.first()
        println("result: $result")
    }

    @Test
    fun cancellableTest() = runBlocking {
        CoroutineScope(EmptyCoroutineContext).launch {
            flowOf(1, 2, 3)
                // 没有 cancellable()：flowOf 不检查取消状态，3 依然会被发射并收集到，输出会多一行 Collected 3（协程要到之后的挂起点才真正结束）。
                .cancellable().onCompletion { throwable ->
                    if (throwable is CancellationException) {
                        println("Flow got cancelled.")
                    }
                }.collect {
                    println("Collected $it")
                    if (it == 2) {
                        cancel()
                    }
                }
        }.join()

        /*
        output:
        Collected 1
        Collected 2
        Flow got cancelled.
         */
    }

    @Test
    fun mapLatestTest() = runBlocking {
        flow {
            repeat(5) {
                val pancakeIndex = it + 1
                println("Emitter:    Start Cooking Pancake $pancakeIndex")
                delay(100)
                println("Emitter:    Pancake $pancakeIndex ready!")
                emit(pancakeIndex)
            }
        }.mapLatest {
            println("Add topping onto the pancake $it")
            delay(200)
            it
        }.collect {
            println("Collector:  Start eating pancake $it")
            delay(300)
            println("Collector:  Finished eating pancake $it")
        }
    }

    @Test
    fun conflateTest() = runBlocking {
        flow {
            repeat(5) {
                println("Emitter:    Start Cooking Pancake $it")
                delay(1.seconds)
                println("Emitter:    Pancake $it ready!")
                emit(it)
            }
        }
            .conflate()
            .collect {
                println("Collector:  Start eating pancake $it")
                delay(3.seconds)
                println("Collector:  Finished eating pancake $it")
            }
    }

    @Test
    fun sharedFlowTest(): Unit = runBlocking {
        // 缓冲区解耦发射者与慢订阅者：extraBufferCapacity 相当于冷流的 buffer() 操作符在热流上的等价物。
        val flow = MutableSharedFlow<Int>(extraBufferCapacity = 10)

        // Collector 1
        launch {
            flow.collect {
                println("Collector 1 processes $it")
            }
        }

        // Collector 2
        launch {
            flow.collect {
                println("Collector 2 processes $it")
                delay(100)
            }
        }

        // Emitter
        launch {
            val timeToEmit = measureTimeMillis {
                repeat(5) {
                    flow.emit(it)
                    delay(10)
                }
            }
            println("Time to emit all values: $timeToEmit ms")
        }

        /*
        注意：SharedFlow 永不完结，两个 collect 永远挂起等新值，所以该程序会一直运行（需手动停止）。
        outpout:
        Collector 1 processes 0
        Collector 2 processes 0
        Collector 1 processes 1
        Collector 1 processes 2
        Collector 1 processes 3
        Collector 1 processes 4
        Time to emit all values: 77 ms
        Collector 2 processes 1
        Collector 2 processes 2
        Collector 2 processes 3
        Collector 2 processes 4
         */
    }

    @Test
    fun stateFlowTest(): Unit = runBlocking {

        val flow = MutableStateFlow(0)

        // Collector 1
        launch {
            flow.collect {
                println("Collector 1 processes $it")
            }
        }

        // Collector 2
        launch {
            flow.collect {
                println("Collector 2 processes $it")
                delay(100)
            }
        }

        // Emitter
        launch {
            val timeToEmit = measureTimeMillis {
                repeat(5) {
                    flow.emit(it)
                    delay(10)
                }
            }
            println("Time to emit all values: $timeToEmit ms")
        }

        /*
        注意：StateFlow 永不完结，两个 collect 永久挂起，程序会一直运行。
        output:
        Collector 1 processes 0
        Collector 2 processes 0
        Collector 1 processes 1
        Collector 1 processes 2
        Collector 1 processes 3
        Collector 1 processes 4
        Time to emit all values: 83 ms
        Collector 2 processes 4
         */
    }

    @Test
    fun launchInTest(): Unit = runBlocking {
        flow {
            emit("Apple")
            emit("Microsoft")

            throw Exception("Network Request Failed!")
        }.onCompletion { cause ->
            if (cause == null) {
                println("Flow completed successfully!")
            } else {
                println("Flow completed exceptionally with $cause")
            }
        }.onEach {
            throw Exception("Exception in collect{}")
        }.catch { throwable ->
            println("Handle exception in catch() operator $throwable")
        }.launchIn(this)
    }

    @Test
    fun main()  = runBlocking {
        val flow = flow {
            delay(100)

            printInfo("Emitting first value")
            emit(1)

            delay(100)

            printInfo("Emitting second value")
            emit(2)
        }

        val scope = CoroutineScope(EmptyCoroutineContext)

        // launchIn 不挂起、立即返回（返回 Job），它把收集扔进作用域里的新协程。两条 launchIn 链是并发运行的两次独立收集，几乎同时开始。
        flow
            .onEach { printInfo("Received $it with launchIn() - 1") }
            .launchIn(scope)

        flow
            .onEach { printInfo("Received $it with launchIn() - 2") }
            .launchIn(scope)

        scope.launch {
            flow.collect {
                printInfo("Received $it in collect - 1")
            }
            flow.collect {
                printInfo("Receive $it in collect - 2")
            }
        }

        Thread.sleep(1000)
    }

    @Test
    fun retryWhenTest() = runBlocking {
        flow {
            repeat(3) { index ->

                delay(1000) // Network call

                if (index < 2) {
                    emit("New Stock data")
                } else {
                    throw IOException("Network Request Failed!")
                }
            }
        }
            .retryWhen { cause, attempt ->
                println("Enter retry() with $cause and attempt is $attempt")
                delay(1000 * (attempt + 1))
                cause is IOException && attempt < 3
            }
            .catch { throwable ->
                println("Handle exception in catch() operator $throwable")
            }.collect { stockData ->
                println("Collected $stockData")
            }
    }

    @Test
    fun joinAllTest() = runBlocking {
        printInfo("main starts")
        joinAll(
            async { coroutine(1, 500) },
            async { coroutine(2, 300) }
        )
        printInfo("main ends")
    }

    suspend fun coroutine(number: Int, delay: Long) {
        printInfo("Coroutine $number starts work")
        delay(delay)
        printInfo("Coroutine $number has finished")
    }

    @Test
    fun invokeOnCompletionTest() = runBlocking {
        val job = launch {
            delay(1.seconds)
            printInfo("Hello")
        }

        // 给 Job 注册一个完成回调，当这个 Job 进入最终状态（Completed 或 Cancelled）时被同步调用恰好一次。
        // 它是普通函数（非挂起），可以在协程外的任何地方调用——这是它和 join() 的一个重要区别。
        job.invokeOnCompletion {
            printInfo("invokeOnCompletion. cause: $it")
        }

        delay(5.milliseconds)
        job.cancel("取消")

        job.join()
    }

}