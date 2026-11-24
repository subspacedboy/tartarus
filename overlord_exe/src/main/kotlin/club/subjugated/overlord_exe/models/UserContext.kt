package club.subjugated.overlord_exe.models

import club.subjugated.overlord_exe.util.ShareableTokenType

data class UserSessionSummary(
    val session: ContractSession,
    val machines: List<SessionMachine>,
    val contract: Contract? = null,
    val subjectHandle: String? = null,
)

data class UserContext(
    val profile: UserProfile,
    val knownTokens: List<UserShareableToken>,
    val activeAsSubject: List<UserSessionSummary>,
    val activeAsKeyholder: List<UserSessionSummary>,
    val completedSessionCount: Long,
) {
    val did: String get() = profile.did
    val handle: String get() = profile.handle?.takeIf { it.isNotBlank() } ?: profile.did
    val convoId: String? get() = profile.convoId

    val primaryShareableToken: String?
        get() = knownTokens.firstOrNull { it.isDefault }?.token ?: knownTokens.firstOrNull()?.token

    fun tokenOfType(type: ShareableTokenType): String? {
        return knownTokens.firstOrNull { it.tokenType == type }?.token
    }

    fun toPromptBlock(): String = buildString {
        appendLine("<user_context>")
        appendLine("handle: @$handle")
        if (profile.lists.isNotEmpty()) {
            appendLine("bluesky_lists: ${profile.lists.joinToString(", ")}")
        }
        appendLine(
            if (knownTokens.isEmpty()) {
                "has_shareable_tokens: no"
            } else {
                "has_shareable_tokens: yes (${knownTokens.size} on file)"
            }
        )
        profile.preferences.defaultIsPublic?.let {
            appendLine("preferred_public: $it")
        }
        if (activeAsSubject.isNotEmpty()) {
            val anyEmergency = activeAsSubject.any { it.contract?.emergencyUnlocked == true }
            appendLine("emergency_unlocked: $anyEmergency")
            val summary = activeAsSubject.joinToString("; ") { s ->
                val machines = s.machines.joinToString("+") { "${it.machineType}:${it.status}" }
                "${s.session.phase}[$machines]"
            }
            appendLine("active_self_sessions: $summary")
        } else {
            appendLine("active_self_sessions: none")
        }
        if (activeAsKeyholder.isNotEmpty()) {
            val held = activeAsKeyholder.joinToString(", ") { s ->
                val target = s.subjectHandle?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "unknown"
                "$target (${s.session.phase})"
            }
            appendLine("holding: $held")
        } else {
            appendLine("holding: none")
        }
        append("</user_context>")
    }
}
