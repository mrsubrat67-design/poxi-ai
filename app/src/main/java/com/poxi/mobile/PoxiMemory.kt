package com.poxi.mobile

import android.content.Context

class PoxiMemory(context: Context) {

    private val prefs = context.getSharedPreferences(
        "poxi_memory",
        Context.MODE_PRIVATE
    )

    fun save(key: String, value: String) {
        prefs.edit().putString(key.lowercase(), value).apply()
    }

    fun get(key: String): String? {
        return prefs.getString(key.lowercase(), null)
    }

    fun delete(key: String) {
        prefs.edit().remove(key.lowercase()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
