package club.subjugated.overlord_exe.util

fun extractShareableToken(shareableToken : String) : String {
    if(shareableToken.contains("/")) {
        return shareableToken.split("/").last()
    }

    return shareableToken
}