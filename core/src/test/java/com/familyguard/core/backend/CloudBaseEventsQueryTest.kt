package com.familyguard.core.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudBaseEventsQueryTest {
    @Test
    fun `admin reconciliation query is scoped by admin ownership`() {
        assertEquals(
            mapOf("kidDeviceId" to "kid-1", "adminUid" to "admin-1"),
            reconcileQueryWhere("kid-1", queryAdminUid = "admin-1"),
        )
    }

    @Test
    fun `kid reconciliation query remains kid scoped for legacy events`() {
        assertEquals(
            mapOf("kidDeviceId" to "kid-1"),
            reconcileQueryWhere("kid-1", queryAdminUid = null),
        )
    }
}
