package club.subjugated.tartarus_coordinator.util

import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.bouncycastle.crypto.CryptoException
import java.util.*

sealed class JwtValidationResult {
    data class AuthorSession(val value: String) : JwtValidationResult()
    data class LockUserSession(val value: String) : JwtValidationResult()
    object Invalid : JwtValidationResult()
}

fun verifyJwt(authorSessionService: AuthorSessionService, lockUserSessionService: LockUserSessionService, jwt: String) : JwtValidationResult {
    val signedJWT = SignedJWT.parse(jwt)
    val claimsSet = signedJWT.jwtClaimsSet

    val sessionTokenName = claimsSet.subject
    try {
        val publicKey = if (sessionTokenName.startsWith("as-")) {
            val authorSession = authorSessionService.findByName(sessionTokenName)
            Base64.getDecoder().decode(authorSession.publicKey)
        } else if (sessionTokenName.startsWith("u-")) {
            val lockUserSession = lockUserSessionService.findByName(sessionTokenName)
            Base64.getDecoder().decode(lockUserSession.publicKey)
        } else {
            return JwtValidationResult.Invalid
        }

        val sessionPublicECKey =
            getECPublicKeyFromCompressedKeyByteArray(
                publicKey
            )
        val verifier = ECDSAVerifier(sessionPublicECKey)

        if(signedJWT.verify(verifier)) {
            if(sessionTokenName.startsWith("as-")) {
                return JwtValidationResult.AuthorSession(sessionTokenName)
            }
            if(sessionTokenName.startsWith("u-")) {
                return JwtValidationResult.LockUserSession(sessionTokenName)
            }
        }
    } catch (ex: CryptoException) {
        return JwtValidationResult.Invalid
    }

    return JwtValidationResult.Invalid
}