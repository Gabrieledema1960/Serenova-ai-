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
            result.add(Skill(obj.getLong("id"), obj.getString("name"), decodeSteps(obj.getJSONArray("steps")), obj.optBoolean("approved", false)))
        }
        return result
    }

    fun save(skills: List<Skill>) {
        val array = JSONArray()
        skills.forEach { skill ->
            array.put(JSONObject().apply {
                put("id", skill.id)
                put("name", skill.name)
                put("approved", skill.approved)
                put("steps", encodeSteps(skill.steps))
            })
        }
        // Synchronous commit makes a newly trained skill survive an immediate app/process kill.
        prefs.edit().putString("skills", array.toString()).commit()
    }

    fun saveTrainingDraft(steps: List<ActionStep>) {
        prefs.edit().putString("training_draft", encodeSteps(steps).toString()).commit()
    }

    fun loadTrainingDraft(): MutableList<ActionStep> = decodeSteps(
        JSONArray(prefs.getString("training_draft", "[]") ?: "[]")
    )

    fun clearTrainingDraft() { prefs.edit().remove("training_draft").commit() }

    private fun encodeSteps(steps: List<ActionStep>) = JSONArray().apply {
        steps.forEach { step ->
            put(JSONObject().apply {
                put("type", step.type.name)
                put("value", step.value)
                put("delayMs", step.delayMs)
            })
        }
    }

    private fun decodeSteps(array: JSONArray) = mutableListOf<ActionStep>().apply {
        for (i in 0 until array.length()) {
            val s = array.getJSONObject(i)
            add(ActionStep(ActionType.valueOf(s.getString("type")), s.optString("value", ""), s.optLong("delayMs", 400)))
        }
    }
}
