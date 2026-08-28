package com.example.sample

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 单元测试中模拟 Android logcat 的日志工具。
 * 输出格式：08-27 10:30:45.123 [main] D/TAG: message
 */
object LogUtils {

    private val FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    private const val DEFAULT_TAG = "LogUtils"

    fun v(msg: Any?, tag: String = DEFAULT_TAG) = log("V", tag, msg)

    fun d(msg: Any?, tag: String = DEFAULT_TAG) = log("D", tag, msg)

    fun i(msg: Any?, tag: String = DEFAULT_TAG) = log("I", tag, msg)

    fun w(msg: Any?, tag: String = DEFAULT_TAG) = log("W", tag, msg)

    fun e(msg: Any?, tag: String = DEFAULT_TAG, throwable: Throwable? = null) {
        log("E", tag, msg)
        throwable?.printStackTrace()
    }

    private fun log(level: String, tag: String, msg: Any?) {
        val time = LocalDateTime.now().format(FORMATTER)
        val thread = Thread.currentThread().name
        println("$time [$thread] $level/$tag: $msg")
    }
}

/** 顶层函数，直接替代 println 使用 */
fun printInfo(msg: Any?) = LogUtils.i(msg)
