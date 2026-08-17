package com.personal.ircclient.core.model

data class IrcMessage(
    val tags: Map<String, String> = emptyMap(),
    val prefix: String? = null,
    val command: String,
    val parameters: List<String> = emptyList(),
    val raw: String,
    var isModifiedByScript: Boolean = false
) {
    companion object {
        fun parse(line: String): IrcMessage? {
            if (line.isBlank()) return null
            
            var remaining = line.trim()
            val tags = mutableMapOf<String, String>()
            
            // 1. Handle IRCv3 Message Tags (e.g. @tag1=val;tag2 :prefix COMMAND...)
            if (remaining.startsWith("@")) {
                val spaceIndex = remaining.indexOf(" ")
                if (spaceIndex != -1) {
                    val rawTags = remaining.substring(1, spaceIndex)
                    rawTags.split(";").forEach { tag ->
                        val parts = tag.split("=", limit = 2)
                        val key = parts[0]
                        val value = if (parts.size > 1) unescapeTagValue(parts[1]) else ""
                        tags[key] = value
                    }
                    remaining = remaining.substring(spaceIndex + 1).trim()
                } else {
                    return null
                }
            }
            
            // 2. Extract Prefix (if exists)
            var prefix: String? = null
            if (remaining.startsWith(":")) {
                val spaceIndex = remaining.indexOf(" ")
                if (spaceIndex != -1) {
                    prefix = remaining.substring(1, spaceIndex)
                    remaining = remaining.substring(spaceIndex + 1).trim()
                }
            }
            
            // 3. Separate main part and trailing parameter
            val parts = remaining.split(" :", limit = 2)
            val mainPart = parts[0]
            val trailing = if (parts.size > 1) parts[1] else null
            
            // 4. Tokenize command and middle parameters
            val mainTokens = mainPart.split(" ").filter { it.isNotEmpty() }
            if (mainTokens.isEmpty()) {
                return null
            }
            
            val command = mainTokens[0].uppercase()
            val params = mainTokens.drop(1).toMutableList()
            if (trailing != null) {
                params.add(trailing)
            }
            
            return IrcMessage(tags, prefix, command, params, line)
        }

        private fun unescapeTagValue(value: String): String {
            return value.replace("\\:", ";")
                .replace("\\s", " ")
                .replace("\\\\", "\\")
                .replace("\\r", "\r")
                .replace("\\n", "\n")
        }
    }
    
    fun build(): String {
        val sb = StringBuilder()
        if (tags.isNotEmpty()) {
            sb.append("@")
            sb.append(tags.entries.joinToString(";") { (k, v) -> if (v.isEmpty()) k else "$k=$v" })
            sb.append(" ")
        }
        if (prefix != null) {
            sb.append(":").append(prefix).append(" ")
        }
        sb.append(command)
        for (i in parameters.indices) {
            sb.append(" ")
            if (i == parameters.size - 1 && (parameters[i].contains(" ") || parameters[i].startsWith(":"))) {
                sb.append(":")
            }
            sb.append(parameters[i])
        }
        return sb.toString()
    }
}
