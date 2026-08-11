package com.example.aiphoneassistant

data class AppInfo(val label: String, val packageName: String)
data class Skill(val id: Long, var name: String, val steps: MutableList<ActionStep>, var approved: Boolean = false)
data class ActionStep(val type: ActionType, val value: String = "", val delayMs: Long = 400)
enum class ActionType { LAUNCH_APP, CLICK_TEXT, CLICK_ID, BACK, HOME, RECENT_APPS, WAIT }
