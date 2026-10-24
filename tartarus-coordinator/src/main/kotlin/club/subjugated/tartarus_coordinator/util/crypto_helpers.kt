package club.subjugated.tartarus_coordinator.util

import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.*
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.signers.StandardDSAEncoding
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMWriter

fun getPemEncoding(ecPublicKey: ECPublicKey): String {
    val stringWriter = StringWriter()
    JcaPEMWriter(stringWriter).use { pemWriter -> pemWriter.writeObject(ecPublicKey) }
    return stringWriter.toString()
}

fun generateECKeyPair(): KeyPair {
    val keyGen = KeyPairGenerator.getInstance("EC", "BC")
    keyGen.initialize(ECGenParameterSpec("secp256r1"))
    return keyGen.generateKeyPair()
}

fun encodePrivateKey(privateKey: PrivateKey): ByteArray {
    // PKCS8, DER
    return privateKey.encoded
}

fun encodePublicKey(publicKey: PublicKey): ByteArray {
    // PKCS8, DER
    return publicKey.encoded
}

fun loadECPublicKeyFromPkcs8(keyBytes: ByteArray): PublicKey {
    Security.addProvider(BouncyCastleProvider())

    val keySpec = X509EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance("EC", "BC")

    return keyFactory.generatePublic(keySpec)
}

fun loadECPublicKeyFromPem(pem: String): ECPublicKey {
    Security.addProvider(BouncyCastleProvider())

    val pemParser = PEMParser(StringReader(pem))
    val obj = pemParser.readObject()
    val keyInfo =
        when (obj) {
            is org.bouncycastle.asn1.x509.SubjectPublicKeyInfo -> obj
            is org.bouncycastle.cert.X509CertificateHolder -> obj.subjectPublicKeyInfo
            else -> throw IllegalArgumentException("Unsupported PEM content")
        }

    val encoded = keyInfo.encoded
    val keyFactory = KeyFactory.getInstance("EC", "BC")
    val keySpec = X509EncodedKeySpec(encoded)
    val pubKey: PublicKey = keyFactory.generatePublic(keySpec)

    return pubKey as ECPublicKey
}

fun encodePublicKeySecp1(publicKey: ECPublicKey): ByteArray {
    val point = publicKey.w
    val xBytes = point.affineX.toByteArray()
    val yBytes = point.affineY.toByteArray()

    // Ensure 32-byte representation for x and y
    val fixedX = ByteArray(32)
    val fixedY = ByteArray(32)

    // Handle cases where the BigInteger representation has a sign byte
    val xStart = if (xBytes.size > 32) 1 else 0
    val yStart = if (yBytes.size > 32) 1 else 0

    // Copy only the lower 32 bytes, ignoring any sign byte if present
    val xLength = xBytes.size - xStart
    val yLength = yBytes.size - yStart

    System.arraycopy(xBytes, xStart, fixedX, 32 - xLength, xLength)
    System.arraycopy(yBytes, yStart, fixedY, 32 - yLength, yLength)

    // Determine the prefix based on the Y-coordinate's parity
    val yIsOdd = fixedY.last().toInt() and 1 == 1
    val prefix: Byte = if (yIsOdd) 0x03 else 0x02

    return byteArrayOf(prefix) + fixedX
}

fun getECPublicKeyFromCompressedKeyByteArray(compressedKeyBytes: ByteArray): ECPublicKey {
    Security.addProvider(BouncyCastleProvider())

    val params: X9ECParameters = SECNamedCurves.getByName("secp256r1")
    val curve: ECCurve = params.curve

    val point = curve.decodePoint(compressedKeyBytes)

    val ecPoint = ECPoint(point.affineXCoord.toBigInteger(), point.affineYCoord.toBigInteger())

    val ecSpec = ECNamedCurveSpec("secp256r1", curve, params.g, params.n, params.h)

    val spec = ECPublicKeySpec(ecPoint, ecSpec)
    val keyFactory = KeyFactory.getInstance("EC", "BC")

    return keyFactory.generatePublic(spec) as ECPublicKey
}

fun decodeSec1PublicKey(sec1Bytes: ByteArray): ECPublicKey =
    getECPublicKeyFromCompressedKeyByteArray(sec1Bytes)

fun isDerEncoded(signature: ByteArray): Boolean {
    if (signature.isEmpty() || signature[0] != 0x30.toByte())
        return false // Check for SEQUENCE (0x30)

    val totalLength = signature[1].toInt() and 0xFF
    if (totalLength + 2 != signature.size) return false // Validate total length

    var index = 2

    // Check for the first INTEGER tag (0x02) for 'r'
    if (signature.getOrNull(index) != 0x02.toByte()) return false
    index++

    // Length of 'r' value
    val rLength = signature.getOrNull(index)?.toInt()?.and(0xFF) ?: return false
    index++

    // Check that 'r' length is valid
    if (index + rLength > signature.size) return false
    index += rLength

    // Check for the second INTEGER tag (0x02) for 's'
    if (signature.getOrNull(index) != 0x02.toByte()) return false
    index++

    // Length of 's' value
    val sLength = signature.getOrNull(index)?.toInt()?.and(0xFF) ?: return false
    index++

    // Check that 's' length is valid
    if (index + sLength != signature.size) return false

    return true
}

fun rawToDerSignature(rawSig: ByteArray): ByteArray {
    if (rawSig.size % 2 != 0) {
        throw IllegalArgumentException("Invalid raw signature length")
    }

    val halfLen = rawSig.size / 2
    val r = BigInteger(1, rawSig.copyOfRange(0, halfLen))
    val s = BigInteger(1, rawSig.copyOfRange(halfLen, rawSig.size))

    // Use BouncyCastle's built-in DSA encoding conversion
    return StandardDSAEncoding.INSTANCE.encode(SECNamedCurves.getByName("secp256r1").n, r, s)
}

fun loadRawPrivateKey(filePath: String): PrivateKey {
    // Read the raw key bytes (32 bytes)
    val keyBytes = File(filePath).readBytes()
    if (keyBytes.size != 32) throw IllegalArgumentException("Invalid private key length")

    // Convert raw bytes to BigInteger
    val privateKeyInt = BigInteger(1, keyBytes)

    // Load the EC parameter spec for secp256r1 (aka prime256v1)
    val ecSpec = ECNamedCurveTable.getParameterSpec("prime256v1")

    // Convert BouncyCastle's ECParameterSpec to a standard Java ECParameterSpec
    val parameterSpec =
        ECNamedCurveSpec("secp256r1", ecSpec.curve, ecSpec.g, ecSpec.n, ecSpec.h, ecSpec.seed)

    // Create ECPrivateKeySpec with the private key integer and the parameter spec
    val privateKeySpec = ECPrivateKeySpec(privateKeyInt, parameterSpec)

    // Create the EC private key
    val keyFactory = KeyFactory.getInstance("EC", "BC")
    return keyFactory.generatePrivate(privateKeySpec)
}

fun derToRawSignature(derSig: ByteArray, outputLength: Int = 64): ByteArray {
    val asn1 = ASN1InputStream(derSig).use { it.readObject() as ASN1Sequence }
    val r = (asn1.getObjectAt(0) as ASN1Integer).positiveValue
    val s = (asn1.getObjectAt(1) as ASN1Integer).positiveValue

    fun bigIntToFixedBytes(b: BigInteger): ByteArray {
        val full = ByteArray(outputLength / 2)
        val raw = b.toByteArray()
        val src = if (raw.size > full.size) raw.copyOfRange(raw.size - full.size, raw.size) else raw
        System.arraycopy(src, 0, full, full.size - src.size, src.size)
        return full
    }

    return bigIntToFixedBytes(r) + bigIntToFixedBytes(s)
}

fun generateSalt(): ByteArray {
    val salt = ByteArray(16)
    SecureRandom().nextBytes(salt)
    return salt
}

fun runSCryptWithCommonParams(input: ByteArray, salt: ByteArray): ByteArray {
    return SCrypt.generate(input, salt, 16384, 8, 1, 32)
}

fun signEcdsaSha256(privateKey: PrivateKey, data: ByteArray): ByteArray {
    // Compute hash
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(data)

    // Sign the hash
    val signer = Signature.getInstance("SHA256withECDSA", "BC")
    signer.initSign(privateKey)
    signer.update(hash)
    return signer.sign()
}

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

private const val BOT_KDF_OKM_BYTES = 48

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
    val okm = ByteArray(BOT_KDF_OKM_BYTES)
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
