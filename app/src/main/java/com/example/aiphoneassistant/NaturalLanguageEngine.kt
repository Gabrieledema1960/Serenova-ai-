package com.example.aiphoneassistant

data class ParsedCommand(val type: CommandType, val target: String = "", val skill: Skill? = null)
enum class CommandType { OPEN_APP, RUN_SKILL, UNKNOWN }

class NaturalLanguageEngine {
    fun parse(text: String, skills: List<Skill>): ParsedCommand {
        val input = text.trim().lowercase()
        val normalized = input.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        val matchingSkill = skills.firstOrNull {
            val n = it.name.lowercase().trim()
            normalized == n || normalized.contains(n) || n.contains(normalized)
        }
        if (matchingSkill != null) return ParsedCommand(CommandType.RUN_SKILL, skill = matchingSkill)

        // Jarvis understands several natural ways of asking to launch an installed app.
        val patterns = listOf(
            Regex("^(?:please )?(?:open|launch|start|run|go to|take me to|bring up) (.+)$"),
            Regex("^(?:can you |could you |would you )?(?:open|launch|start|run) (.+)$"),
            Regex("^(?:i want to |i need to )?(?:open|launch|start) (.+)$")
        )
        for (pattern in patterns) {
            val match = pattern.find(normalized) ?: continue
            val target = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (target.isNotBlank()) return ParsedCommand(CommandType.OPEN_APP, cleanAppTarget(target))
        }

        // Common conversational requests.
        val appName = normalized.removePrefix("the ").trim()
        if (appName.isNotBlank() && appName.split(' ').size <= 4) {
            val likelyAppWords = listOf("whatsapp", "youtube", "facebook", "instagram", "telegram", "chrome", "messages", "camera", "settings", "phone", "gallery")
            if (likelyAppWords.any { appName.contains(it) }) {
                return ParsedCommand(CommandType.OPEN_APP, cleanAppTarget(appName))
            }
        }
        return ParsedCommand(CommandType.UNKNOWN)
    }

    private fun cleanAppTarget(value: String): String {
        return value
            .replace(Regex("\\b(app|application)\\b"), "")
            .replace(Regex("\\b(for me|please|now|on my phone)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
