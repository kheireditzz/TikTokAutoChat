package com.autochat.floating

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class CoordPreset(
    val name: String,
    val chatX: Int,
    val chatY: Int,
    val sendX: Int,
    val sendY: Int
)

object PresetManager {
    private const val PREFS_KEY = "saved_presets_json"

    fun getPresets(prefs: SharedPreferences): MutableList<CoordPreset> {
        val list = mutableListOf<CoordPreset>()
        val jsonStr = prefs.getString(PREFS_KEY, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        CoordPreset(
                            name = obj.optString("name", "Preset $i"),
                            chatX = obj.optInt("chatX", 25),
                            chatY = obj.optInt("chatY", 96),
                            sendX = obj.optInt("sendX", 92),
                            sendY = obj.optInt("sendY", 94)
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        if (list.isEmpty()) {
            // Default bawaan yang praktis
            list.add(CoordPreset("TikTok Live Biasa", 25, 96, 92, 94))
            list.add(CoordPreset("TikTok Live PK / Shop", 28, 95, 94, 95))
            list.add(CoordPreset("Posisi Tengah (Testing)", 50, 50, 60, 50))
            savePresets(prefs, list)
        }
        return list
    }

    fun savePresets(prefs: SharedPreferences, presets: List<CoordPreset>) {
        val arr = JSONArray()
        for (p in presets) {
            val obj = JSONObject().apply {
                put("name", p.name)
                put("chatX", p.chatX)
                put("chatY", p.chatY)
                put("sendX", p.sendX)
                put("sendY", p.sendY)
            }
            arr.put(obj)
        }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
    }

    fun addOrUpdatePreset(prefs: SharedPreferences, preset: CoordPreset) {
        val current = getPresets(prefs)
        val existingIndex = current.indexOfFirst { it.name.equals(preset.name, ignoreCase = true) }
        if (existingIndex >= 0) {
            current[existingIndex] = preset
        } else {
            current.add(preset)
        }
        savePresets(prefs, current)
    }
}
