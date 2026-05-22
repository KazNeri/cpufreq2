package com.yin.cpufreq2.core.cpu

import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

data class CoreUsage(
    val coreIndex: Int,
    val usagePercent: Float,
    val currentFreqKhz: Long = 0,
    val isOnline: Boolean = true
)

class CpuUsageMonitor {

    private val avgFreqMonitor = CpuAvgFreqMonitor()

    companion object {
        private const val TAG = "CPUUsage"
        private const val USER_HZ = 100
        private const val SUSPEND_THRESHOLD_MS = 5_000L
    }

    private var lastProcStat: Map<Int, StatData>? = null
    private var lastWallTimeMsProcStat = 0L
    private var useProcStat = true

    private var lastTotalJiffies: LongArray? = null
    private var lastIdleMicros: LongArray? = null
    private var lastWallTimeMsSysfs = 0L

    fun getUsages(): List<CoreUsage> {
        if (useProcStat) {
            val result = getUsagesViaProcStat()
            if (result != null) {
                return result
            } else {
                useProcStat = false
                Log.w(TAG, "fallback to sysfs (time_in_state + cpuidle)")
            }
        }
        return getUsagesViaSysfs()
    }

    private fun getUsagesViaProcStat(): List<CoreUsage>? {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val curProcStat = readProcStat(cpuCount) ?: return null
        if (curProcStat.isEmpty()) return null

        val curWallTimeMs = SystemClock.elapsedRealtime()
        val results = mutableListOf<CoreUsage>()

        if (lastProcStat == null || lastWallTimeMsProcStat == 0L) {
            for (i in 0 until cpuCount) {
                val isOnline = curProcStat.containsKey(i)
                val freq = if (isOnline) readScalingCurFreq(i) else 0L
                results.add(CoreUsage(i, 0f, freq, isOnline))
            }
            lastProcStat = curProcStat
            lastWallTimeMsProcStat = curWallTimeMs
            return results
        }

        val dWallMs = curWallTimeMs - lastWallTimeMsProcStat
        val prevTotalSum = lastProcStat!!.values.sumOf { it.total }
        val curTotalSum = curProcStat.values.sumOf { it.total }
        val totalDeltaSum = curTotalSum - prevTotalSum

        if (dWallMs > SUSPEND_THRESHOLD_MS && totalDeltaSum <= 0) {
            Log.w(TAG, "procstat: Suspend detected, resetting baseline")
            lastProcStat = curProcStat
            lastWallTimeMsProcStat = curWallTimeMs
            for (i in 0 until cpuCount) {
                val isOnline = curProcStat.containsKey(i)
                val freq = if (isOnline) readScalingCurFreq(i) else 0L
                results.add(CoreUsage(i, 0f, freq, isOnline))
            }
            return results
        }

        for (i in 0 until cpuCount) {
            val cur = curProcStat[i]
            val prev = lastProcStat!![i]

            if (cur == null || prev == null) {
                val isOnline = cur != null
                val freq = if (isOnline) readScalingCurFreq(i) else 0L
                results.add(CoreUsage(i, 0f, freq, isOnline))
                continue
            }

            val dTotal = cur.total - prev.total
            val dIdle = cur.idle - prev.idle
            val usage = if (dTotal > 0) {
                ((dTotal - dIdle).toFloat() / dTotal * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }

            val freq = readScalingCurFreq(i)
            results.add(CoreUsage(i, usage, freq, true))
        }

        lastProcStat = curProcStat
        lastWallTimeMsProcStat = curWallTimeMs
        Log.d(TAG, "procstat results: $results")
        return results
    }

    private fun getUsagesViaSysfs(): List<CoreUsage> {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val curTotalJiffies = LongArray(cpuCount)
        val curIdleMicros = LongArray(cpuCount)
        val onlineFlags = BooleanArray(cpuCount)

        for (i in 0 until cpuCount) {
            if (!isCoreOnline(i)) {
                onlineFlags[i] = false
                continue
            }
            try {
                curTotalJiffies[i] = readTotalTimeInState(i)
                curIdleMicros[i] = readTotalCpuIdleMicros(i)
                onlineFlags[i] = true
                Log.d(TAG, "Core[$i] online: totalJiffies=${curTotalJiffies[i]}, idleMicros=${curIdleMicros[i]}")
            } catch (e: Exception) {
                onlineFlags[i] = false
                Log.w(TAG, "Core[$i] read stats failed (${e.message}), forced offline")
            }
        }

        val curWallTimeMs = SystemClock.elapsedRealtime()
        val results = mutableListOf<CoreUsage>()

        if (lastTotalJiffies == null || lastIdleMicros == null || lastWallTimeMsSysfs == 0L) {
            for (i in 0 until cpuCount) {
                results.add(CoreUsage(i, 0f, 0L, onlineFlags[i]))
            }
            lastTotalJiffies = curTotalJiffies
            lastIdleMicros = curIdleMicros
            lastWallTimeMsSysfs = curWallTimeMs
            return results
        }

        val dWallMs = curWallTimeMs - lastWallTimeMsSysfs
        val wallJiffies = dWallMs * USER_HZ / 1_000

        val totalJiffiesDelta = (0 until cpuCount).sumOf { i ->
            if (onlineFlags[i]) curTotalJiffies[i] - lastTotalJiffies!![i] else 0L
        }
        if (dWallMs > SUSPEND_THRESHOLD_MS && totalJiffiesDelta <= 0) {
            Log.w(TAG, "sysfs: Suspend detected, resetting baseline")
            lastTotalJiffies = curTotalJiffies
            lastIdleMicros = curIdleMicros
            lastWallTimeMsSysfs = curWallTimeMs
            for (i in 0 until cpuCount) {
                results.add(CoreUsage(i, 0f, 0L, onlineFlags[i]))
            }
            return results
        }

        for (i in 0 until cpuCount) {
            if (!onlineFlags[i]) {
                results.add(CoreUsage(i, 0f, 0L, false))
                continue
            }

            val dTotal = curTotalJiffies[i] - lastTotalJiffies!![i]
            val dIdleMicros = curIdleMicros[i] - lastIdleMicros!![i]

            val freq = avgFreqMonitor.getAvgFreqSinceLastCall(i)
            val usage = calculateUsageViaSysfs(dTotal, dIdleMicros, wallJiffies, i)
            results.add(CoreUsage(i, usage, freq, true))
        }

        lastTotalJiffies = curTotalJiffies
        lastIdleMicros = curIdleMicros
        lastWallTimeMsSysfs = curWallTimeMs

        Log.d(TAG, "sysfs results: $results")
        return results
    }

    private fun calculateUsageViaSysfs(
        dTotalJiffies: Long,
        dIdleMicros: Long,
        wallJiffies: Long,
        coreIndex: Int
    ): Float {
        if (dIdleMicros == 0L && dTotalJiffies >= 0) return 0f
        val dIdleJiffies = dIdleMicros.toFloat() / 10_000f
        val activeJiffies = (dTotalJiffies.toFloat() - dIdleJiffies).coerceAtLeast(0f)
        return (activeJiffies / wallJiffies * 100f).coerceIn(0f, 100f)
    }

    private fun isCoreOnline(cpuIndex: Int): Boolean {
        return try {
            val onlineFile = File("/sys/devices/system/cpu/cpu$cpuIndex/online")
            if (!onlineFile.exists()) true
            else onlineFile.readText().trim() == "1"
        } catch (e: Exception) { true }
    }

    private fun readTotalTimeInState(cpuIndex: Int): Long {
        val file = File("/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/stats/time_in_state")
        return file.readLines().sumOf { line ->
            line.trim().split("\\s+".toRegex())[1].toLong()
        }
    }

    private fun readTotalCpuIdleMicros(cpuIndex: Int): Long {
        var total = 0L
        var stateIndex = 0
        while (true) {
            val stateFile = File("/sys/devices/system/cpu/cpu$cpuIndex/cpuidle/state$stateIndex/time")
            if (!stateFile.exists()) break
            total += stateFile.readText().trim().toLong()
            stateIndex++
        }
        if (stateIndex == 0) throw Exception("No cpuidle states")
        return total
    }

    private fun readScalingCurFreq(cpuIndex: Int): Long {
        return try {
            val path = "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/scaling_cur_freq"
            File(path).readText().trim().toLong()
        } catch (e: Exception) {
            try {
                val altPath = "/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_cur_freq"
                File(altPath).readText().trim().toLong()
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to read current freq for CPU$cpuIndex")
                0L
            }
        }
    }

    private data class StatData(val total: Long, val idle: Long)

    private fun readProcStat(cpuCount: Int): Map<Int, StatData>? {
        val map = mutableMapOf<Int, StatData>()
        return try {
            RandomAccessFile("/proc/stat", "r").use { raf ->
                var line: String?
                while (raf.readLine().also { line = it } != null) {
                    val parts = line!!.split(Regex("\\s+"))
                    if (parts[0].startsWith("cpu") && parts[0] != "cpu") {
                        val coreIndex = parts[0].substring(3).toIntOrNull() ?: continue
                        if (coreIndex >= cpuCount) continue
                        val user = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                        val nice = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                        val system = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                        val idle = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                        val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                        val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                        val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
                        val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L

                        val total = user + nice + system + irq + softirq + steal + idle + iowait
                        val idleTotal = idle + iowait
                        map[coreIndex] = StatData(total, idleTotal)
                    }
                }
            }
            map
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read /proc/stat", e)
            null
        }
    }
}