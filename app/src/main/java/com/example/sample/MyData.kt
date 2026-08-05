package com.example.sample;

import androidx.annotation.Keep

@Keep
data class MyData(
    val code: Int,
    val name: String? = null
)