package club.subjugated.overlord_exe.util

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Security
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

fun generateECKeyPair(): KeyPair {
    val keyGen = KeyPairGenerator.getInstance("EC", "BC")
    keyGen.initialize(ECGenParameterSpec("secp256r1"))
    return keyGen.generateKeyPair()
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
