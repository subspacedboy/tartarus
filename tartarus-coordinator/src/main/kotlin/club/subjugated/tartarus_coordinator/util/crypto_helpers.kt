package club.subjugated.tartarus_coordinator.util

import java.io.StringWriter
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.*
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.signers.StandardDSAEncoding
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import org.bouncycastle.math.ec.ECCurve
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

fun encodePublicKeySecp1(publicKey: ECPublicKey): ByteArray {
    val point = publicKey.w
    val xBytes = point.affineX.toByteArray()
    val yBytes = point.affineY.toByteArray()

    // Ensure 32-byte representation for x and y
    val fixedX = ByteArray(32) { 0 }
    System.arraycopy(xBytes, 0, fixedX, 32 - xBytes.size, xBytes.size)

    val yIsOdd = yBytes.last().toInt() and 1 == 1
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

fun isDerEncoded(signature: ByteArray): Boolean {
    if (signature.isEmpty() || signature[0] != 0x30.toByte()) return false // Check for SEQUENCE (0x30)

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
