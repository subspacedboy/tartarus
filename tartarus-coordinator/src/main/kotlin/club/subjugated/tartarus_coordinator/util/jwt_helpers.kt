package club.subjugated.tartarus_coordinator.util

import club.subjugated.tartarus_coordinator.models.BotState
import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.BotService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory

private val jwtLogger =
    LoggerFactory.getLogger("club.subjugated.tartarus_coordinator.util.jwt_helpers")

sealed class JwtValidationResult {
    data class AuthorSession(val value: String) : JwtValidationResult()

    data class LockUserSession(val value: String) : JwtValidationResult()

    data class AdminSession(val value: String) : JwtValidationResult()

    data class BotSession(val value: String) : JwtValidationResult()

    object Invalid : JwtValidationResult()
}

@Suppress("TooGenericExceptionCaught")
fun verifyJwt(
    authorSessionService: AuthorSessionService,
    lockUserSessionService: LockUserSessionService,
    jwt: String,
    adminSessionService: AdminSessionService? = null,
    botService: BotService? = null,
): JwtValidationResult {
    var validationResult: JwtValidationResult = JwtValidationResult.Invalid
    try {
        val signedJWT = SignedJWT.parse(jwt)
        val sessionTokenName = signedJWT.jwtClaimsSet.subject
        val candidate =
            resolveCandidateSession(
                sessionTokenName,
                authorSessionService,
                lockUserSessionService,
                adminSessionService,
                botService,
            )
        if (candidate != null) {
            val (publicKey, expectedResult) = candidate
            val sessionPublicECKey = getECPublicKeyFromCompressedKeyByteArray(publicKey)
            if (signedJWT.verify(ECDSAVerifier(sessionPublicECKey))) {
                validationResult = expectedResult
            } else {
                jwtLogger.warn(
                    "ECDSA signature verification failed for session: {}",
                    sessionTokenName,
                )
            }
        }
    } catch (ex: Exception) {
        jwtLogger.warn("Exception during verifyJwt: {}", ex.message, ex)
    }
    return validationResult
}

private fun resolveCandidateSession(
    sessionTokenName: String?,
    authorSessionService: AuthorSessionService,
    lockUserSessionService: LockUserSessionService,
    adminSessionService: AdminSessionService?,
    botService: BotService?,
): Pair<ByteArray, JwtValidationResult>? {
    if (sessionTokenName == null) return null
    return when {
        sessionTokenName.startsWith("as-") ->
            authorSessionService.findByName(sessionTokenName).decodePublicKey() to
                JwtValidationResult.AuthorSession(sessionTokenName)
        sessionTokenName.startsWith("u-") ->
            lockUserSessionService.findByName(sessionTokenName).decodePublicKey() to
                JwtValidationResult.LockUserSession(sessionTokenName)
        sessionTokenName.startsWith("ad-") && adminSessionService != null ->
            adminSessionService.findByName(sessionTokenName).decodePublicKey() to
                JwtValidationResult.AdminSession(sessionTokenName)
        sessionTokenName.startsWith("b-") && botService != null -> {
            val bot = botService.getByName(sessionTokenName)
            if (bot.state == BotState.ACTIVE) {
                bot.decodePublicKey() to JwtValidationResult.BotSession(sessionTokenName)
            } else {
                null
            }
        }
        else -> null
    }
}
