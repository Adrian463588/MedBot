package com.medbot.app.data.online

/** Small RFC 9309-compatible matcher for the source hosts used by MedBot. */
internal object RobotsTxtPolicy {
    fun isAllowed(body: String, path: String, userAgent: String = "MedBot"): Boolean {
        val groups = mutableListOf<Group>()
        var agents = mutableListOf<String>()
        var rules = mutableListOf<Rule>()

        fun flush() {
            if (agents.isNotEmpty()) groups += Group(agents.toList(), rules.toList())
            agents = mutableListOf()
            rules = mutableListOf()
        }

        body.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isBlank()) {
                flush()
                return@forEach
            }
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            when (key) {
                "user-agent" -> {
                    if (rules.isNotEmpty()) flush()
                    if (value.isNotBlank()) agents += value.lowercase()
                }
                "allow", "disallow" -> if (agents.isNotEmpty() && value.isNotBlank()) {
                    rules += Rule(key == "allow", value)
                }
            }
        }
        flush()

        val normalizedPath = path.ifBlank { "/" }
        val applicable = groups.filter { group ->
            group.agents.any { it == "*" || userAgent.lowercase().contains(it) }
        }
        val matchingRules = applicable.flatMap { it.rules }.filter { normalizedPath.startsWith(it.path) }
        if (matchingRules.isEmpty()) return true
        val strongest = matchingRules.maxByOrNull { it.path.length } ?: return true
        return strongest.allow
    }

    private data class Group(val agents: List<String>, val rules: List<Rule>)
    private data class Rule(val allow: Boolean, val path: String)
}
