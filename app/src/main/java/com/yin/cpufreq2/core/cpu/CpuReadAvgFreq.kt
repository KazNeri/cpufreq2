package com.yin.cpufreq2.core.cpu

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class CpuAvgFreqMonitor {

    companion object {
        private const val TAG = "CpuAvgFreqMonitor"
    }

    private val lastTimeInState = mutableMapOf<Int, Map<Long, Long>>()

    @Synchronized
    fun getAvgFreqSinceLastCall(cpuIndex: Int): Long {
        val currentData = readTimeInState(cpuIndex) ?: return -1
        val lastData = lastTimeInState[cpuIndex]

        if (lastData == null) {
            lastTimeInState[cpuIndex] = currentData
            Log.d(TAG, "First call for CPU$cpuIndex, store baseline.")
            return 0L
        }

        var totalTimeJiffies = 0L
        var totalFreqTime = 0L   // freq(kHz) * time(jiffies)

        for ((freq, curTime) in currentData) {
            val lastTime = lastData[freq] ?: 0L
            val delta = curTime - lastTime
            if (delta > 0) {
                totalTimeJiffies += delta
                totalFreqTime += freq * delta
            }
        }

        lastTimeInState[cpuIndex] = currentData

        if (totalTimeJiffies == 0L) {
            Log.w(TAG, "No valid time delta for CPU$cpuIndex")
            return -1
        }

        val avgFreq = totalFreqTime / totalTimeJiffies
        Log.d(TAG, "CPU$cpuIndex avg freq = $avgFreq kHz (delta time = ${totalTimeJiffies * 10} ms)")
        return avgFreq
    }

    private fun readTimeInState(cpuIndex: Int): Map<Long, Long>? {
        val file = File("/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/stats/time_in_state")
        if (!file.exists() || !file.isFile) {
            Log.w(TAG, "time_in_state not found for CPU$cpuIndex")
            return null
        }

        return try {
            file.readLines().mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size == 2) {
                    val freq = parts[0].toLong()
                    val time = parts[1].toLong()
                    freq to time
                } else null
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read time_in_state for CPU$cpuIndex", e)
            null
        }
    }
}


val dataHistory : MutableMap<Int,MutableMap<Int,MutableList<IntArray>>> = mutableMapOf()
val dataCurrent : MutableMap<Int,MutableList<IntArray>> = mutableMapOf()
var cpu_cores = 0
val cpu_cluster : MutableList<IntArray> = mutableListOf()


val cpu_cluster_list : MutableMap<Int,Unit?> = mutableMapOf()
val dataCurrentMutex = Mutex()
var slotSwitch:Boolean = false
val executor:ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
var oldDuration:Long = 0

fun cpuGetRawDataTester(duration:Long)
{
    if(duration != oldDuration)
    {
        executor.scheduleAtFixedRate(::cpuFloderReader,0,duration,TimeUnit.MILLISECONDS)
        oldDuration = duration
    }
}

fun cpuGetRawDataTesterStop()
{
    if(oldDuration != 0L)
    {
        executor.shutdown()
        oldDuration = 0L
    }
}

fun cpuFloderReader()
{
    //var dataHistory = mutableListOf<MutableList<MutableList<IntArray>>>()
    if(cpu_cores == 0)
    {
        var cpu_num = 0
        while(true)
        {
            val floder = File("/sys/devices/system/cpu/cpu$cpu_num")
            if (floder.exists() && floder.isDirectory)
            {
                cpu_num++
            } else {
                break
            }
        }
        cpu_cores = cpu_num
    }
    for(cpu_num in 0 until cpu_cores)
    {
        //Log.i("CPU_read","cpu$cpu_num,OK")
        val file = File("/sys/devices/system/cpu/cpu$cpu_num/cpufreq/stats/time_in_state")
        if (file.exists() && file.isFile)
        {

            if(cpu_cluster_list.size < cpu_cores){
                cpuClusterGet(cpu_num)
            }
            if(dataHistory[cpu_num].isNullOrEmpty())
            {
                dataHistory[cpu_num] = mutableMapOf()
            }
            if(dataHistory[cpu_num]!![if (slotSwitch) 1 else 0].isNullOrEmpty())
            {
                dataHistory[cpu_num]!![if (slotSwitch) 1 else 0] = mutableListOf()
            }
            dataHistory[cpu_num]!![if (slotSwitch) 1 else 0] = cpuFloderReadFiles(cpu_num,file).toMutableList()
            if(!dataHistory[cpu_num]!![if (slotSwitch) 0 else 1].isNullOrEmpty())
            {
                if(dataCurrent[cpu_num].isNullOrEmpty())
                {
                    dataCurrent[cpu_num] = mutableListOf()
                }
                runBlocking {
                    dataCurrentMutex.withLock {
                        dataCurrent[cpu_num] = cpuFilesClockTimeProcess(
                            dataHistory[cpu_num]!![0]!!.toTypedArray(),
                            dataHistory[cpu_num]!![1]!!.toTypedArray()
                        ).toMutableList()
                    }

                }
            }
        }else{
            if(!dataHistory[cpu_num].isNullOrEmpty()){
                if((!dataHistory[cpu_num]!![0].isNullOrEmpty()))
                    dataHistory[cpu_num]!!.remove(0)
                if((!dataHistory[cpu_num]!![1].isNullOrEmpty()))
                    dataHistory[cpu_num]!!.remove(1)
            }
            if(!dataCurrent[cpu_num].isNullOrEmpty())
                dataCurrent.remove(cpu_num)
            Log.i("CPU_read","cpu$cpu_num,shutdown")
        }
    }
    slotSwitch = !slotSwitch
}

fun cpuClusterGet(cpu_num:Int):Unit
{
    if(true)
    {
        val file = File("/sys/devices/system/cpu/cpu$cpu_num/cpufreq/related_cpus")
        if(file.isFile)
        {
            val string = file.readLines()
            Log.i("cpuClusterGet",string[0])
            val parts = string[0].split(" ")
            val int_list = mutableListOf<Int>()
            val first_num = parts[0].toInt()
            for(i in first_num until first_num + parts.size){
                if(!cpu_cluster_list.containsKey(i)){
                    cpu_cluster_list[i] = null
                }
                int_list.add(i)
            }
            cpu_cluster.add(int_list.toIntArray())
        }
    }
}

fun cpuFilesClockTimeProcess(dataSlot01:Array<IntArray>,dataSlot02:Array<IntArray>):Array<IntArray>
{
    val dataList = mutableListOf<IntArray>()
    val minSize = minOf(dataSlot01.size,dataSlot02.size)
    for(i in 0 until minSize)
    {
        val freq = dataSlot01[i][0].toInt()
        val time = abs(dataSlot01[i][1].toInt() - dataSlot02[i][1].toInt())
        //Log.i("CPU","freq:$freq,time:$time")
        dataList.add(intArrayOf(freq,time))
    }
    return dataList.toTypedArray()
}

fun cpuFloderReadFiles(cpuNum:Int,file:File):Array<IntArray>
{
    val dataList = mutableListOf<IntArray>()
    BufferedReader(FileReader(file)).use{reader ->
        var buffer:String?
        while(reader.readLine().also { buffer = it }!= null)
        {
            if(!buffer.isNullOrEmpty()){
                buffer!!.split("\n").forEach{
                    if(buffer!!.trim().isNotEmpty()){
                        val parts = buffer!!.split(" ")
                        if(parts.size == 2){
                            val freq = parts[0].toInt()
                            val time = parts[1].toInt()
                            //Log.i("CPU_${cpuNum}","freq:$freq,time:$time")
                            dataList.add(intArrayOf(freq,time))
                        }

                    }
                }
            }
        }
    }
    return dataList.toTypedArray()
}

suspend fun cpuGetAvaliableList():List<Int>
{
    dataCurrentMutex.withLock {
        val list:MutableList<Int> = mutableListOf()
        dataCurrent.forEach {
            list.add(it.key)
        }
        return list.toList()
    }
}

suspend fun cpuGetFilteredData(cpu_num:Int):Array<IntArray>?
{
    dataCurrentMutex.withLock {
        return if(!dataCurrent[cpu_num].isNullOrEmpty())
        {
            dataCurrent[cpu_num]!!.toTypedArray()
        } else {
            null
        }

    }
}

suspend fun cpuGetMinMaxClockSpeed(cpu_num:Int): Array<Int>
{
    dataCurrentMutex.withLock {
        return if(!dataCurrent[cpu_num].isNullOrEmpty())
        {
            val target = dataCurrent[cpu_num]!!.toTypedArray()
            arrayOf(target[0][0],target[target.size - 1][0])
        } else {
            arrayOf(0,0)
        }
    }
}

fun cpuGetAvgClockSpeed(cpu_list:Array<IntArray>):Int
{
    var cpu_tick :Int = 0
    var cpu_avg_clock = 0
    cpu_list.forEach {
        if(it[1] != 0)
        {
            cpu_avg_clock += it[1] * it[0]
            cpu_tick += it[1]
        }
    }
    return cpu_avg_clock / cpu_tick
}

fun cpuGetClusterFreq(cluster_num :Int):Array<IntArray>?
{
    val cpu_cluser_data = cpu_cluster[cluster_num]
    if(cpu_cluser_data.isNotEmpty()){
        cpu_cluser_data.forEach {
            var ret :Array<IntArray>?
            runBlocking {
                ret = cpuGetFilteredData(it)
            }
            if(!ret.isNullOrEmpty())
                return ret
        }
    }
    return null
}