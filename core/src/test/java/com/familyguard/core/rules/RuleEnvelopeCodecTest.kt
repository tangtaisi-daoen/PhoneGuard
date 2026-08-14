package com.familyguard.core.rules

import com.google.gson.JsonParser
import com.familyguard.core.data.RuleSetEnvelope
import com.familyguard.core.data.TemporaryAllowance
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEnvelopeCodecTest {

    @Test
    fun `legacy single ruleset migrates to all profiles`() {
        val legacy = JsonParser.parseString(
            """{"appLimits":[],"categoryLimits":[],"dailyTotal":{"totalMinutes":45},"version":7}""",
        ).asJsonObject

        val envelope = RuleEnvelopeCodec.parseCompatible(legacy)

        assertEquals(45, envelope.weekdayProfile.dailyTotal.totalMinutes)
        assertEquals(45, envelope.weekendProfile.dailyTotal.totalMinutes)
        assertEquals(45, envelope.holidayProfile.dailyTotal.totalMinutes)
        assertEquals(7, envelope.revision)
    }

    @Test
    fun `temporary allowances survive envelope serialization`() {
        val original = RuleSetEnvelope(
            temporaryAllowances = listOf(
                TemporaryAllowance("example.app", 30, 2_000, "临时加时", 1_000),
            ),
        )

        val decoded = RuleEnvelopeCodec.fromJson(RuleEnvelopeCodec.toJson(original))

        assertEquals(original.temporaryAllowances, decoded?.temporaryAllowances)
    }
}
