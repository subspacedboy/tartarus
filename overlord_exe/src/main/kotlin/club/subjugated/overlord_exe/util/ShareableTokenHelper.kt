package club.subjugated.overlord_exe.util

enum class ShareableTokenType {
    S,
    TC,
    OTHER,
}

private val SHAREABLE_TOKEN_REGEX = Regex("\\b((?:s|tc)-[0-9a-zA-Z]{7})\\b")

fun extractShareableToken(shareableToken: String): String {
    val trimmed = shareableToken.trim()
    if (trimmed.contains("/")) {
        return trimmed.substringAfterLast("/").trim()
    }
    return trimmed
}

fun extractAllShareableTokens(text: String): List<String> {
    return SHAREABLE_TOKEN_REGEX.findAll(text)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
}

fun inferShareableTokenType(token: String): ShareableTokenType {
    return when {
        token.startsWith("tc-", ignoreCase = true) -> ShareableTokenType.TC
        token.startsWith("s-", ignoreCase = true) -> ShareableTokenType.S
        else -> ShareableTokenType.OTHER
    }
}
