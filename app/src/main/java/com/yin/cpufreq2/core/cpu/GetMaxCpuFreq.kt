package com.yin.cpufreq2.core.cpu

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun getMaxCpuFreqKhz(): Long {
    var trueMaxFreq = 0L
    val availableCores = Runtime.getRuntime().availableProcessors()

    for (i in 0 until availableCores) {
        val hardwareMaxFreq = readIntFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
        val freq = hardwareMaxFreq ?: readIntFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq")

        if (freq != null && freq > trueMaxFreq) {
            trueMaxFreq = freq
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && freq == null) {
            continue
        }
    }

    return if (trueMaxFreq > 0L) trueMaxFreq else 2_400_000L
}

private fun readIntFromFile(path: String): Long? {
    return try {
        File(path).readText().trim().toLongOrNull()
    } catch (e: Exception) {
        null
    }
}
