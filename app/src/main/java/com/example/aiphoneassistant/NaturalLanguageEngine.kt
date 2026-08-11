package com.example.aiphoneassistant

data class ParsedCommand(val type: CommandType, val target: String = "", val skill: Skill? = null)
enum class CommandType { OPEN_APP, RUN_SKILL, UNKNOWN }

class NaturalLanguageEngine {
    fun parse(text: String, skills: List<Skill>): ParsedCommand {
        val input = text.trim().lowercase()
        val matchingSkill = skills.firstOrNull { val n = it.name.lowercase(); input == n || input.contains(n) || n.contains(input) }
        if (matchingSkill != null) return ParsedCommand(CommandType.RUN_SKILL, skill = matchingSkill)
        val prefixes = listOf("please open ", "please launch ", "open ", "launch ", "start ")
        prefixes.firstOrNull { input.startsWith(it) }?.let { return ParsedCommand(CommandType.OPEN_APP, input.removePrefix(it).trim()) }
        return ParsedCommand(CommandType.UNKNOWN)
    }
}
