package club.subjugated.overlord_exe.util

import org.bouncycastle.asn1.sec.ECPrivateKey as BcECPrivateKey
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

const val BOT_KDF_SALT = "tartarus-bot-kdf-v1"

fun bigIntToFixed32Bytes(b: BigInteger): ByteArray {
    val full = ByteArray(32)
    val raw = b.toByteArray()
    val src = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
    System.arraycopy(src, 0, full, 32 - src.size, src.size)
    return full
}

fun extractRawScalarFromPrivateKey(privateKey: PrivateKey): ByteArray {
    Security.addProvider(BouncyCastleProvider())
    val ecPriv =
        when (privateKey) {
            is ECPrivateKey -> privateKey
            else -> {
                val keyFactory = KeyFactory.getInstance("EC", "BC")
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKey.encoded)) as ECPrivateKey
            }
        }
    return bigIntToFixed32Bytes(ecPriv.s)
}

fun keyPairFromRawScalar(scalar: BigInteger): KeyPair {
    Security.addProvider(BouncyCastleProvider())
    val params: X9ECParameters = SECNamedCurves.getByName("secp256r1")
    val ecSpec = ECNamedCurveSpec("secp256r1", params.curve, params.g, params.n, params.h)
    val keyFactory = KeyFactory.getInstance("EC", "BC")

    val privSpec = ECPrivateKeySpec(scalar, ecSpec)
    val privateKey = keyFactory.generatePrivate(privSpec)

    val qPoint = params.g.multiply(scalar).normalize()
    val pubPoint = ECPoint(qPoint.affineXCoord.toBigInteger(), qPoint.affineYCoord.toBigInteger())
    val pubSpec = ECPublicKeySpec(pubPoint, ecSpec)
    val publicKey = keyFactory.generatePublic(pubSpec)

    return KeyPair(publicKey, privateKey)
}

fun deriveBotKeyPairFromSeed(
    masterSeed32Bytes: ByteArray,
    internalName: String,
    keyVersion: Int = 1,
): KeyPair {
    require(masterSeed32Bytes.size == 32) { "Master seed must be 32 bytes" }
    require(internalName.isNotBlank()) { "Bot internalName must not be blank" }
    require(keyVersion >= 1) { "keyVersion must be >= 1" }

    val saltBytes = BOT_KDF_SALT.toByteArray(StandardCharsets.UTF_8)
    val infoBytes = "$internalName:$keyVersion".toByteArray(StandardCharsets.UTF_8)

    val hkdf = HKDFBytesGenerator(SHA256Digest())
    hkdf.init(HKDFParameters(masterSeed32Bytes, saltBytes, infoBytes))
    val okm = ByteArray(48)
    hkdf.generateBytes(okm, 0, okm.size)

    val params: X9ECParameters = SECNamedCurves.getByName("secp256r1")
    val nMinusOne = params.n.subtract(BigInteger.ONE)
    val k = BigInteger(1, okm)
    val scalar = k.mod(nMinusOne).add(BigInteger.ONE)

    return keyPairFromRawScalar(scalar)
}

fun deriveBotKeyPair(
    authorPrivateKey: PrivateKey,
    internalName: String,
    keyVersion: Int = 1,
): KeyPair {
    val seed = extractRawScalarFromPrivateKey(authorPrivateKey)
    return deriveBotKeyPairFromSeed(seed, internalName, keyVersion)
}

fun loadKeyPairFromBase64Pkcs8(base64Key: String): KeyPair {
    Security.addProvider(BouncyCastleProvider())
    val cleaned =
        base64Key
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN EC PRIVATE KEY-----", "")
            .replace("-----END EC PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
    val keyBytes = Base64.getDecoder().decode(cleaned)
    val scalar: BigInteger =
        if (keyBytes.size == 32) {
            BigInteger(1, keyBytes)
        } else {
            try {
                val keyFactory = KeyFactory.getInstance("EC", "BC")
                val privateKey =
                    keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes)) as ECPrivateKey
                privateKey.s
            } catch (_: Exception) {
                val sec1Key = BcECPrivateKey.getInstance(keyBytes)
                sec1Key.key
            }
        }
    return keyPairFromRawScalar(scalar)
}

fun loadPrivateKeyFromPkcs8Bytes(keyBytes: ByteArray): PrivateKey {
    Security.addProvider(BouncyCastleProvider())
    if (keyBytes.size == 32) {
        return keyPairFromRawScalar(BigInteger(1, keyBytes)).private
    }
    return try {
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    } catch (_: Exception) {
        val sec1Key = BcECPrivateKey.getInstance(keyBytes)
        keyPairFromRawScalar(sec1Key.key).private
    }
}

fun signDoubleSha256Raw(privateKey: PrivateKey, data: ByteArray): ByteArray {
    Security.addProvider(BouncyCastleProvider())
    val hash = MessageDigest.getInstance("SHA-256").digest(data)
    val derSig =
        Signature.getInstance("SHA256withECDSA", "BC")
            .apply {
                initSign(privateKey)
                update(hash)
            }
            .sign()
    return derToRawSignature(derSig)
}

fun createEs256Jwt(subject: String, privateKey: PrivateKey, ttlSeconds: Long = 3600): String {
    Security.addProvider(BouncyCastleProvider())
    val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    val now = System.currentTimeMillis() / 1000
    val exp = now + ttlSeconds

    val headerJson = """{"alg":"ES256","typ":"JWT"}"""
    val payloadJson = """{"sub":"$subject","name":"$subject","iat":$now,"exp":$exp}"""

    val headerB64 = urlEncoder.encodeToString(headerJson.toByteArray(StandardCharsets.UTF_8))
    val payloadB64 = urlEncoder.encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))
    val signingInput = "$headerB64.$payloadB64"

    val derSig =
        Signature.getInstance("SHA256withECDSA", "BC")
            .apply {
                initSign(privateKey)
                update(signingInput.toByteArray(StandardCharsets.UTF_8))
            }
            .sign()
    val rawSig = derToRawSignature(derSig)
    val sigB64 = urlEncoder.encodeToString(rawSig)

    return "$signingInput.$sigB64"
}
