package com.familyguard.core.rules

import com.familyguard.core.data.DateOverride
import com.familyguard.core.data.RuleProfile
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.RuleSetEnvelope
import com.familyguard.core.data.TemporaryAllowance
import com.google.gson.Gson
import com.google.gson.JsonObject

object RuleEnvelopeCodec {
    private val gson = Gson()

    fun toJson(envelope: RuleSetEnvelope): String = gson.toJson(envelope)

    fun fromJson(json: String): RuleSetEnvelope? = runCatching {
        parseCompatible(gson.fromJson(json, JsonObject::class.java))
    }.getOrNull()

    /** 同时接受新 envelope 和旧单套 RuleSet，旧规则迁移为三套相同配置。 */
    fun parseCompatible(json: JsonObject): RuleSetEnvelope {
        if (!json.has("weekdayProfile")) {
            val legacy = RuleCodec.parse(json)
            return RuleSetEnvelope(
                revision = legacy.version,
                weekdayProfile = legacy,
                weekendProfile = legacy,
                holidayProfile = legacy,
            )
        }
        return RuleSetEnvelope(
            schemaVersion = json.get("schemaVersion")?.asInt ?: 1,
            revision = json.get("revision")?.asLong ?: 0L,
            timezoneId = json.get("timezoneId")?.asString ?: "Asia/Shanghai",
            weekdayProfile = parseProfile(json, "weekdayProfile"),
            weekendProfile = parseProfile(json, "weekendProfile"),
            holidayProfile = parseProfile(json, "holidayProfile"),
            dateOverrides = json.getAsJsonArray("dateOverrides")?.mapNotNull { element ->
                runCatching {
                    val item = element.asJsonObject
                    DateOverride(
                        localDate = item.get("localDate")?.asString.orEmpty(),
                        profile = RuleProfile.valueOf(item.get("profile")?.asString ?: "HOLIDAY"),
                        label = item.get("label")?.asString.orEmpty(),
                    )
                }.getOrNull()
            }.orEmpty(),
            temporaryAllowances = json.getAsJsonArray("temporaryAllowances")?.mapNotNull { element ->
                runCatching {
                    val item = element.asJsonObject
                    TemporaryAllowance(
                        packageName = item.get("packageName")?.asString.orEmpty(),
                        extraMinutes = item.get("extraMinutes")?.asInt ?: 0,
                        expiresAt = item.get("expiresAt")?.asLong ?: 0L,
                        reason = item.get("reason")?.asString.orEmpty(),
                        createdAt = item.get("createdAt")?.asLong ?: 0L,
                    )
                }.getOrNull()
            }.orEmpty(),
            effectiveAt = json.get("effectiveAt")?.asLong ?: 0L,
            generatedAt = json.get("generatedAt")?.asLong ?: 0L,
        )
    }

    private fun parseProfile(json: JsonObject, name: String): RuleSet {
        val profile = json.getAsJsonObject(name) ?: return RuleSet()
        return RuleCodec.parse(profile)
    }
}
