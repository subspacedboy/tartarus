package club.subjugated.tartarus_coordinator.util

import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory

private val jwtLogger = LoggerFactory.getLogger("club.subjugated.tartarus_coordinator.util.jwt_helpers")

sealed class JwtValidationResult {
    data class AuthorSession(val value: String) : JwtValidationResult()

    data class LockUserSession(val value: String) : JwtValidationResult()

    data class AdminSession(val value: String) : JwtValidationResult()

    object Invalid : JwtValidationResult()
}

fun verifyJwt(
    authorSessionService: AuthorSessionService,
    lockUserSessionService: LockUserSessionService,
    jwt: String,
    adminSessionService: AdminSessionService? = null,
): JwtValidationResult {
    try {
        val signedJWT = SignedJWT.parse(jwt)
        val claimsSet = signedJWT.jwtClaimsSet

        val sessionTokenName = claimsSet.subject ?: return JwtValidationResult.Invalid
        val publicKey =
            if (sessionTokenName.startsWith("as-")) {
                val authorSession = authorSessionService.findByName(sessionTokenName)
                authorSession.decodePublicKey()
            } else if (sessionTokenName.startsWith("u-")) {
                val lockUserSession = lockUserSessionService.findByName(sessionTokenName)
                lockUserSession.decodePublicKey()
            } else if (sessionTokenName.startsWith("ad-") && adminSessionService != null) {
                val adminSession = adminSessionService.findByName(sessionTokenName)
                adminSession.decodePublicKey()
            } else {
                return JwtValidationResult.Invalid
            }

        val sessionPublicECKey = getECPublicKeyFromCompressedKeyByteArray(publicKey)
        val verifier = ECDSAVerifier(sessionPublicECKey)

        if (signedJWT.verify(verifier)) {
            if (sessionTokenName.startsWith("as-")) {
                return JwtValidationResult.AuthorSession(sessionTokenName)
            }
            if (sessionTokenName.startsWith("u-")) {
                return JwtValidationResult.LockUserSession(sessionTokenName)
            }
            if (sessionTokenName.startsWith("ad-")) {
                return JwtValidationResult.AdminSession(sessionTokenName)
            }
        } else {
            jwtLogger.warn("ECDSA signature verification failed for session: {}", sessionTokenName)
        }
    } catch (ex: Exception) {
        jwtLogger.warn("Exception during verifyJwt: {}", ex.message, ex)
        return JwtValidationResult.Invalid
    }

    return JwtValidationResult.Invalid
}
