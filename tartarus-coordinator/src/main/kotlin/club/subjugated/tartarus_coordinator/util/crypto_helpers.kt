package club.subjugated.tartarus_coordinator.util

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.signers.StandardDSAEncoding
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Security
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

fun getPemEncoding(ecPublicKey: ECPublicKey): String {
    val stringWriter = StringWriter()
    JcaPEMWriter(stringWriter).use { pemWriter ->
        pemWriter.writeObject(ecPublicKey)
    }
    return stringWriter.toString()
}

fun getECPublicKeyFromByteArray(compressedKeyBytes : ByteArray) : ECPublicKey {
    Security.addProvider(BouncyCastleProvider())

    val params: X9ECParameters = SECNamedCurves.getByName("secp256r1")
    val curve: ECCurve = params.curve

    val point = curve.decodePoint(compressedKeyBytes)

    val ecPoint = ECPoint(
        point.affineXCoord.toBigInteger(),
        point.affineYCoord.toBigInteger()
    )

    val ecSpec = ECNamedCurveSpec(
        "secp256r1",
        curve,
        params.g,
        params.n,
        params.h
    )

    val spec = ECPublicKeySpec(ecPoint, ecSpec)
    val keyFactory = KeyFactory.getInstance("EC", "BC")

    return keyFactory.generatePublic(spec) as ECPublicKey
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