package com.familyguard.core.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `pending invite with future expiry is binding-eligible`() {
        val now = 1_000_000L
        val doc = mapOf<String, Any?>(
            "status" to "PENDING",
            "expiresAt" to (now + 60_000L),
        )
        assertTrue(CloudBaseBindings.isBindingEligible(doc, now))
    }

    @Test
    fun `pending invite without expiry stays eligible for backward compatibility`() {
        assertTrue(CloudBaseBindings.isBindingEligible(mapOf("status" to "PENDING"), 1_000_000L))
    }

    @Test
    fun `expired invite is rejected`() {
        val now = 1_000_000L
        val doc = mapOf<String, Any?>(
            "status" to "PENDING",
            "expiresAt" to (now - 1L),
        )
        assertFalse(CloudBaseBindings.isBindingEligible(doc, now))
    }

    @Test
    fun `bound or expired-status invites are rejected`() {
        assertFalse(CloudBaseBindings.isBindingEligible(mapOf("status" to "BOUND"), 1L))
        assertFalse(CloudBaseBindings.isBindingEligible(mapOf("status" to "EXPIRED"), 1L))
    }

    @Test
    fun `selects the most recently created invite code`() {
        val rows = listOf(
            mapOf<String, Any?>("inviteCode" to "aaa111", "createdAt" to 100L),
            mapOf<String, Any?>("inviteCode" to "bbb222", "createdAt" to 300L),
            mapOf<String, Any?>("inviteCode" to "ccc333", "createdAt" to 200L),
        )
        assertEquals("bbb222", CloudBaseBindings.selectLatestInviteCode(rows))
        assertNull(CloudBaseBindings.selectLatestInviteCode(emptyList()))
    }

    @Test
    fun `latest invite selection prefers rows with createdAt`() {
        val rows = listOf(
            mapOf<String, Any?>("inviteCode" to "old", "createdAt" to 500L),
            mapOf<String, Any?>("inviteCode" to "no-date"),
        )
        assertEquals("old", CloudBaseBindings.selectLatestInviteCode(rows))
    }
}
