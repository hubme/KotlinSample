package com.example.sample

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ExceptionTest {

    /*
    1. scope.launch { ... } 是非阻塞的，它只是"安排"协程在未来执行，然后立即返回。所以 try 块实际上瞬间就执行完了。
    2. 真正的异常发生在异步执行的协程内部，与 try-catch 已经不在同一个调用栈上了
     */
    @Test
    fun tryCatchTest1() {
        val scope = CoroutineScope(Job())
        try {
            scope.launch {
                functionThatThrowsIt()
            }
        } catch (e: Exception) {
            // 无法捕获异常
            println("Caught: $e")
        }

        Thread.sleep(100)
    }

    fun functionThatThrowsIt() {
        throw RuntimeException()
    }

    @Test
    fun tryCatchTest2() {
        val scope = CoroutineScope(Job())
        scope.launch {
            try {
                functionThatThrowsIt()
            } catch (e: Exception) {
                // 可以捕获异常
                println("Caught: $e")
            }
        }
        Thread.sleep(100)
    }

    @Test
    fun exceptionHandlerTest() = runBlocking {
        val handler = CoroutineExceptionHandler { _, e ->
            println("thread: ${Thread.currentThread()} Caught: $e")
        }
        CoroutineScope(handler).launch {
            throw RuntimeException("Something went wrong")
        }.join()
    }

    @Test
    fun exceptionHandlerTest2() = runBlocking {
        val handler = CoroutineExceptionHandler { _, e ->
            println("thread: ${Thread.currentThread()} Caught: $e")
        }

        // 2. 级联取消	父协程被取消 → 所有兄弟协程也被取消
        CoroutineScope(handler).launch {
            val job1 = launch {
                println("Starting coroutine 1")
                delay(100)
                // 1. 异常向上传播	子协程的未捕获异常会取消父协程
                // 3. 在子协程内部 try-catch 阻止异常向上传播，保护兄弟协程
                throw RuntimeException()
            }

            val job2 = launch {
                println("Starting coroutine 2")
                // job1 协程抛出了异常，导致 job2 协程也被取消
                delay(3000)
                println("Coroutine 2 completed")
            }
        }.join()

    }
}