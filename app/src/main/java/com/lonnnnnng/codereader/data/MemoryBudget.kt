package com.lonnnnnng.codereader.data

import android.app.ActivityManager
import android.content.Context

/** 源码阅读的内存边界，整文件上限始终小于分段读取可承受的设备预算。 @author long */
data class ReaderMemoryBudget(
    val maxWholeFileBytes: Long,
    val largeFileThresholdBytes: Long,
)

/**
 * 将设备内存映射为稳定的阅读边界，避免高内存设备被固定 1 MB 阈值过早切入只读模式。
 * @author long
 */
object MemoryBudgetPolicy {
    const val MIN_WHOLE_FILE_BYTES = 8L * 1024 * 1024
    const val MAX_WHOLE_FILE_BYTES = 20L * 1024 * 1024
    const val MIN_LARGE_FILE_THRESHOLD_BYTES = 512L * 1024
    // 保持 1 MB 作为高内存设备上限，避免普通源码在手机上突然从可编辑切换成只读分段模式。
    const val MAX_LARGE_FILE_THRESHOLD_BYTES = 1L * 1024 * 1024

    fun fromMemoryBytes(memoryBytes: Long): ReaderMemoryBudget {
        val safeMemory = memoryBytes.coerceAtLeast(MIN_WHOLE_FILE_BYTES)
        val wholeFile = (safeMemory / 8).coerceIn(MIN_WHOLE_FILE_BYTES, MAX_WHOLE_FILE_BYTES)
        val threshold = (safeMemory / 128).coerceIn(
            MIN_LARGE_FILE_THRESHOLD_BYTES,
            MAX_LARGE_FILE_THRESHOLD_BYTES,
        )
        return ReaderMemoryBudget(
            maxWholeFileBytes = wholeFile,
            largeFileThresholdBytes = threshold,
        )
    }
}

/** 允许 JVM/设备测试注入确定的内存预算，避免测试依赖宿主机内存。 @author long */
fun interface MemoryBudgetProvider {
    fun current(): ReaderMemoryBudget
}

/** 生产设备的内存预算来源；取系统 memoryClass 与运行时上限中更保守的一项。 @author long */
class AndroidMemoryBudgetProvider(context: Context) : MemoryBudgetProvider {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    override fun current(): ReaderMemoryBudget {
        val runtimeBytes = Runtime.getRuntime().maxMemory()
        val classBytes = activityManager?.memoryClass?.toLong()?.times(1024 * 1024L) ?: runtimeBytes
        return MemoryBudgetPolicy.fromMemoryBytes(minOf(runtimeBytes, classBytes))
    }
}
