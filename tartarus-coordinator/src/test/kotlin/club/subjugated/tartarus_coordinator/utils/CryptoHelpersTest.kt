package club.subjugated.tartarus_coordinator.utils

import club.subjugated.tartarus_coordinator.util.encodePublicKey
import club.subjugated.tartarus_coordinator.util.encodePublicKeySecp1
import club.subjugated.tartarus_coordinator.util.generateECKeyPair
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.getPemEncoding
import club.subjugated.tartarus_coordinator.util.loadECPublicKeyFromPem
import club.subjugated.tartarus_coordinator.util.loadECPublicKeyFromPkcs8
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.security.Security
import java.util.*

class CryptoHelpersTest {

    @Test
    fun testReadCompressedPublicPoint() {
        val key = "As+NFaAML8BX9lHC1FO6k3pM0/NetPBVo9lev1/DqCfP"
        val ecPublicKey = getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(key))
        assertThat(ecPublicKey).isNotNull()
    }

    @Test
    fun testReadPemPublic() {
        val key = """
            -----BEGIN PUBLIC KEY-----
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEi1Fon/zVd/Xu3066aPztAsJKdNJm
            TU2GzNM846j/IaA8c+POQ3N3PtLo0jpsHh7cH3ii7mxeHjrhdLaLbZKnQw==
            -----END PUBLIC KEY-----
        """.trimIndent()
        loadECPublicKeyFromPem(key)
    }

    @Test
    fun testGetPemEncoding() {
        val key = "As+NFaAML8BX9lHC1FO6k3pM0/NetPBVo9lev1/DqCfP"
        val ecPublicKey = getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(key))
        val pemresult = getPemEncoding(ecPublicKey)

        assertThat(pemresult).contains("--BEGIN PUBLIC KEY--")
    }

    @Test
    fun testReadPkcs8Der() {
        val keypair = generateECKeyPair()
        val derBytes = encodePublicKey(keypair.public)

        val ecPublicKey = loadECPublicKeyFromPkcs8(derBytes)
        assertThat(ecPublicKey).isNotNull()
    }

    @Test
    fun testEncodeSecp1Compressed() {
        val key = "As+NFaAML8BX9lHC1FO6k3pM0/NetPBVo9lev1/DqCfP"
        val ecPublicKey = getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(key))

        val result = Base64.getEncoder().encodeToString(encodePublicKeySecp1(ecPublicKey))
        assertThat(result).isEqualTo(key)
    }

    @Test
    fun testGenerateKeypair() {
        val keypair = generateECKeyPair()
        assertThat(keypair).isNotNull()
        assertThat(keypair.private).isInstanceOf(ECPrivateKey::class.java)
        assertThat(keypair.public).isInstanceOf(ECPublicKey::class.java)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun installBouncyCastle(): Unit {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}