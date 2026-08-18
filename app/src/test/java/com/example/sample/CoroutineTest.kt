package com.example.sample

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

class CoroutineTest {

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

        val pairs = generateSequence(Pair(0, 1)) { Pair(it.second, it.first + it.second) }
            .map { it.first }

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
        val result = sequenceOf(1, 2, 3, 4, 5)
            .map {
                println("map $it");
                it * 2
            }
            .filter {
                println("filter $it");
                it > 4
            }
            .first()
        println("result: $result")
    }
}