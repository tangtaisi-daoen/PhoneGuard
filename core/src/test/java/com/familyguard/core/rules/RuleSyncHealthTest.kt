package com.familyguard.core.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleSyncHealthTest {
    @Test
    fun `new revision stays pending during grace period then becomes stale`() {
        assertEquals(
            RuleSyncHealth.PENDING,
            evaluateRuleSync(2, 1, generatedAt = 1_000, nowMs = 1_000 + 299_999, graceMs = 300_000),
        )
        assertEquals(
            RuleSyncHealth.STALE,
            evaluateRuleSync(2, 1, generatedAt = 1_000, nowMs = 1_000 + 300_000, graceMs = 300_000),
        )
    }

    @Test
    fun `matching or newer applied revision is synced`() {
        assertEquals(RuleSyncHealth.SYNCED, evaluateRuleSync(2, 2, 1_000, 9_000, 300_000))
        assertEquals(RuleSyncHealth.SYNCED, evaluateRuleSync(2, 3, 1_000, 9_000, 300_000))
    }
}
