package com.poxi.mobile

import android.content.Context

class PoxiMemory(context: Context) {

    private val prefs = context.getSharedPreferences(
        "poxi_memory",
        Context.MODE_PRIVATE
    )

    fun save(key: String, value: String) {
        prefs.edit()
            .putString(normalizeKey(key), value.trim())
            .apply()
    }

    fun get(key: String): String? {
        return prefs.getString(
            normalizeKey(key),
            null
        )
    }

    fun delete(key: String) {
        prefs.edit()
            .remove(normalizeKey(key))
            .apply()
    }

    fun clear() {
        prefs.edit()
            .clear()
            .apply()
    }

    fun has(key: String): Boolean {
        return prefs.contains(normalizeKey(key))
    }

    fun saveName(name: String) {
        save("name", name)
    }

    fun getName(): String? {
        return get("name")
    }

    fun saveFact(fact: String) {
        save("note_memory", fact)
    }

    fun getFact(): String? {
        return get("note_memory")
    }

    fun saveLastNote(note: String) {
        save("last_note", note)
    }

    fun getLastNote(): String? {
        return get("last_note")
    }

    private fun normalizeKey(key: String): String {
        return key.trim().lowercase()
    }
}
