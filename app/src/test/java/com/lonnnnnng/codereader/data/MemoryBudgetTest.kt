package com.lonnnnnng.codereader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** @author long */
class MemoryBudgetTest {
    @Test
    fun `低内存设备使用更保守的整文件预算`() {
        val budget = MemoryBudgetPolicy.fromMemoryBytes(64L * 1024 * 1024)

        assertEquals(8L * 1024 * 1024, budget.maxWholeFileBytes)
        assertEquals(MemoryBudgetPolicy.MIN_LARGE_FILE_THRESHOLD_BYTES, budget.largeFileThresholdBytes)
    }

    @Test
    fun `高内存设备仍遵守产品硬上限`() {
        val budget = MemoryBudgetPolicy.fromMemoryBytes(8L * 1024 * 1024 * 1024)

        assertEquals(MemoryBudgetPolicy.MAX_WHOLE_FILE_BYTES, budget.maxWholeFileBytes)
        assertEquals(MemoryBudgetPolicy.MAX_LARGE_FILE_THRESHOLD_BYTES, budget.largeFileThresholdBytes)
        assertTrue(budget.largeFileThresholdBytes < budget.maxWholeFileBytes)
    }
}
