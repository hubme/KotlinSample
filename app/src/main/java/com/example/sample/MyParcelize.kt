package com.example.sample;

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class MyParcelize(
	val code: Int,
    val name: String? = null
) : Parcelable