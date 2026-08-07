package com.personal.ircclient.core.model

data class IrcMessage(
    val prefix: String? = null,
    val command: String,
    val parameters: List<String> = emptyList(),
    val raw: String,
    var isModifiedByScript: Boolean = false
) {
    companion object {
        fun parse(line: String): IrcMessage? {
            if (line.isBlank()) return null
            
            var remaining = line
            var prefix: String? = null
            
            if (remaining.startsWith(":")) {
                val spaceIndex = remaining.indexOf(" ")
                if (spaceIndex != -1) {
                    prefix = remaining.substring(1, spaceIndex)
                    remaining = remaining.substring(spaceIndex + 1).trim()
                }
            }
            
            val parts = remaining.split(" :", limit = 2)
            val mainPart = parts[0]
            val trailing = if (parts.size > 1) parts[1] else null
            
            val mainTokens = mainPart.split(" ").filter { it.isNotEmpty() }
            if (mainTokens.isEmpty()) return null
            
            val command = mainTokens[0]
            val params = mainTokens.drop(1).toMutableList()
            if (trailing != null) {
                params.add(trailing)
            }
            
            return IrcMessage(prefix, command, params, line)
        }
    }
    
    fun build(): String {
        val sb = StringBuilder()
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
