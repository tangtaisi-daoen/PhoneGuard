package com.familyguard.admin

import com.familyguard.core.categories.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLimitRowDataTest {

    @Test
    fun `minutes input updates row and valid row becomes app limit`() {
        val row = AppLimitRowData(packageName = "com.example.game", category = AppCategory.GAME)

        row.updateMinutes(" 45 ")

        assertEquals(45, row.minutes)
        assertEquals(45, row.toAppLimitOrNull()?.dailyMinutes)
        assertEquals(AppCategory.GAME, row.toAppLimitOrNull()?.category)
    }

    @Test
    fun `invalid minutes or package cannot become app limit`() {
        val invalidMinutes = AppLimitRowData(packageName = "com.example.game")
        invalidMinutes.updateMinutes("")
        val invalidPackage = AppLimitRowData(packageName = "not a package", minutes = 30)

        assertNull(invalidMinutes.toAppLimitOrNull())
        assertNull(invalidPackage.toAppLimitOrNull())
    }

    @Test
    fun `spinner position maps all supported categories`() {
        AppCategory.entries.forEach { category ->
            assertEquals(category, AppLimitRowData.categoryAt(AppLimitRowData.positionOf(category)))
        }
    }

    @Test
    fun `add action reuses an existing empty row`() {
        val empty = AppLimitRowData()
        val rows = mutableListOf(AppLimitRowData(packageName = "com.example.used"), empty)

        val result = chooseRowForNewLimit(rows)

        assertEquals(empty, result.row)
        assertEquals(false, result.inserted)
        assertEquals(2, rows.size)
    }

    @Test
    fun `add action appends a row when every row is already selected`() {
        val rows = mutableListOf(AppLimitRowData(packageName = "com.example.used"))

        val result = chooseRowForNewLimit(rows)

        assertEquals(true, result.inserted)
        assertEquals(1, result.position)
        assertEquals(result.row, rows.last())
    }
}
