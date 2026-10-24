package club.subjugated.tartarus_coordinator.util

import java.math.BigInteger
import java.security.Security
import java.security.interfaces.ECPublicKey
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class CryptoHelpersTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setupBc() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun deriveBotKeyPairIsDeterministicAndIsolatedByInternalNameAndVersion() {
        val authorKeyPair = generateECKeyPair()

        val timerV1A = deriveBotKeyPair(authorKeyPair.private, "timer", 1)
        val timerV1B = deriveBotKeyPair(authorKeyPair.private, "timer", 1)
        val timerV2 = deriveBotKeyPair(authorKeyPair.private, "timer", 2)
        val announcerV1 = deriveBotKeyPair(authorKeyPair.private, "announcer", 1)

        val timerV1PubA =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(timerV1A.public as ECPublicKey))
        val timerV1PubB =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(timerV1B.public as ECPublicKey))
        val timerV2Pub =
            Base64.getEncoder().encodeToString(encodePublicKeySecp1(timerV2.public as ECPublicKey))
        val announcerPub =
            Base64.getEncoder()
                .encodeToString(encodePublicKeySecp1(announcerV1.public as ECPublicKey))

        assertThat(timerV1PubA).isEqualTo(timerV1PubB)
        assertThat(timerV1A.private.encoded).isEqualTo(timerV1B.private.encoded)
        assertThat(timerV1PubA).isNotEqualTo(timerV2Pub)
        assertThat(timerV1PubA).isNotEqualTo(announcerPub)
    }

    @Test
    fun deriveBotKeyPairFromFixedSeedProducesConsistentKey() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val authorKeyPair = keyPairFromRawScalar(BigInteger(1, seed))
        val fromSeed = deriveBotKeyPairFromSeed(seed, "superBot", 1)
        val fromAuthorKey = deriveBotKeyPair(authorKeyPair.private, "superBot", 1)

        assertThat(encodePublicKeySecp1(fromSeed.public as ECPublicKey))
            .isEqualTo(encodePublicKeySecp1(fromAuthorKey.public as ECPublicKey))
    }
}
