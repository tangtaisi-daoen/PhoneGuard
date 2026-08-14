package com.familyguard.kid.update

import android.content.Context

object UpdateDeliveryStore {
    private const val PREFERENCES = "update_delivery_status"

    fun load(context: Context): UpdateDeliveryStatus {
        val values = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .all.mapValues { it.value?.toString().orEmpty() }
        return UpdateDeliveryStatusCodec.decode(values)
    }

    fun save(context: Context, status: UpdateDeliveryStatus) {
        val editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear()
        UpdateDeliveryStatusCodec.encode(status).forEach(editor::putString)
        editor.apply()
    }

    fun update(context: Context, transform: (UpdateDeliveryStatus) -> UpdateDeliveryStatus): UpdateDeliveryStatus {
        val next = transform(load(context))
        save(context, next)
        return next
    }
}
