package com.familyguard.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TimeRange 语义测试：同天内 [start, end)，跨天（start > end）按次日凌晨处理。
 */
class TimeRangeTest {

    @Test
    fun `同天时间段内命中`() {
        val range = TimeRange(10 * 60, 12 * 60) // 10:00 - 12:00
        assertTrue(range.contains(10 * 60)) // 10:00 含
        assertTrue(range.contains(11 * 60 + 30))
        assertFalse(range.contains(9 * 60 + 59))
        assertFalse(range.contains(12 * 60)) // 12:00 不含（半开区间）
    }

    @Test
    fun `跨天时间段正确换日`() {
        val range = TimeRange(22 * 60, 7 * 60) // 22:00 - 次日 07:00
        assertTrue(range.contains(23 * 60)) // 23:00
        assertTrue(range.contains(6 * 60 + 59)) // 06:59
        assertFalse(range.contains(21 * 60 + 59)) // 21:59
        assertFalse(range.contains(7 * 60)) // 07:00 不含
    }

    @Test
    fun `零长度时间段永不命中`() {
        val range = TimeRange(8 * 60, 8 * 60)
        assertFalse(range.contains(8 * 60))
    }

    @Test
    fun `全天时间段命中任何时刻`() {
        val range = TimeRange(0, 24 * 60)
        assertTrue(range.contains(0))
        assertTrue(range.contains(12 * 60))
        assertTrue(range.contains(23 * 60 + 59))
    }
}
