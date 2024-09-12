package com.task.servicediscovery.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random


fun getCurrentYearMonth(): String {
    val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).time)
}

fun generatePortInRange(from: Int, to: Int): Int {
    val random = Random.nextInt(from, to)
    return random
}