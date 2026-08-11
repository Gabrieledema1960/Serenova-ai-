package com.example.aiphoneassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SkillStore(context: Context) {
    private val prefs = context.getSharedPreferences("assistant_v3", Context.MODE_PRIVATE)
    fun load(): MutableList<Skill> {
        val result = mutableListOf<Skill>()
        val array = JSONArray(prefs.getString("skills", "[]") ?: "[]")
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val steps = mutableListOf<ActionStep>()
            val sa = obj.getJSONArray("steps")
            for (j in 0 until sa.length()) {
                val s = sa.getJSONObject(j)
                steps.add(ActionStep(ActionType.valueOf(s.getString("type")), s.optString("value", ""), s.optLong("delayMs", 400)))
            }
            result.add(Skill(obj.getLong("id"), obj.getString("name"), steps, obj.optBoolean("approved", false)))
        }
        return result
    }
    fun save(skills: List<Skill>) {
        val array = JSONArray()
        skills.forEach { skill ->
            val obj = JSONObject().apply { put("id", skill.id); put("name", skill.name); put("approved", skill.approved) }
            val steps = JSONArray()
            skill.steps.forEach { step -> steps.put(JSONObject().apply { put("type", step.type.name); put("value", step.value); put("delayMs", step.delayMs) }) }
            obj.put("steps", steps); array.put(obj)
        }
        prefs.edit().putString("skills", array.toString()).apply()
    }
}
