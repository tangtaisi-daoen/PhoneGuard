package com.familyguard.core.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudBaseBindingsTest {

    @Test
    fun `selects the most recently bound device when history has multiple rows`() {
        val rows = listOf(
            mapOf<String, Any?>("kidDeviceId" to "old", "boundAt" to 100L),
            mapOf<String, Any?>("kidDeviceId" to "current", "boundAt" to 300L),
            mapOf<String, Any?>("kidDeviceId" to "middle", "boundAt" to 200L),
        )

        assertEquals("current", CloudBaseBindings.selectLatestBoundDeviceId(rows))
    }

    @Test
    fun `ignores rows without a usable device id`() {
        val rows = listOf(
            mapOf<String, Any?>("kidDeviceId" to "", "boundAt" to 500L),
            mapOf<String, Any?>("kidDeviceId" to "valid", "boundAt" to 100L),
        )

        assertEquals("valid", CloudBaseBindings.selectLatestBoundDeviceId(rows))
        assertNull(CloudBaseBindings.selectLatestBoundDeviceId(emptyList()))
    }
}
