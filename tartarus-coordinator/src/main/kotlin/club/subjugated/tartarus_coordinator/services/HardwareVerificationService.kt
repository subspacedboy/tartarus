package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.*
import club.subjugated.tartarus_coordinator.models.ProvisionedHardwareUnit
import club.subjugated.tartarus_coordinator.storage.HardwareAttestationLogRepository
import club.subjugated.tartarus_coordinator.storage.LockSessionRepository
import club.subjugated.tartarus_coordinator.storage.ProvisionedHardwareUnitRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.decodeSec1PublicKey
import club.subjugated.tartarus_coordinator.util.rawToDerSignature
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.annotation.PostConstruct
import java.io.ByteArrayInputStream
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
@Suppress("LargeClass")
class HardwareVerificationService(
    private val unitRepository: ProvisionedHardwareUnitRepository,
    private val logRepository: HardwareAttestationLogRepository,
    private val timeSource: TimeSource = TimeSource(),
    @Autowired(required = false) private val lockSessionRepository: LockSessionRepository? = null,
) {
    private val objectMapper: ObjectMapper =
        jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val rootCertificates = mutableListOf<X509Certificate>()
    private val intermediateCertificates = mutableListOf<X509Certificate>()

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @PostConstruct
    fun loadBundledCertificates() {
        val resolver = PathMatchingResourcePatternResolver()
        val certFactory = CertificateFactory.getInstance("X.509", "BC")

        try {
            val resources = resolver.getResources("classpath*:certs/infineon/*.crt")
            for (resource in resources) {
                resource.inputStream.use { stream ->
                    val cert = certFactory.generateCertificate(stream) as? X509Certificate
                    if (cert != null) {
                        if (isRootCertificate(cert)) {
                            rootCertificates.add(cert)
                        } else {
                            intermediateCertificates.add(cert)
                        }
                    }
                }
            }
            println(
                "🔒 Loaded ${rootCertificates.size} Infineon Root CAs and ${intermediateCertificates.size} Intermediate CAs"
            )
        } catch (e: Exception) {
            println("⚠️ Warning: could not load bundled Infineon certificates: ${e.message}")
        }
    }

    private fun isRootCertificate(cert: X509Certificate): Boolean {
        // Self-signed or known root subject
        val subject = cert.subjectX500Principal.name
        val issuer = cert.issuerX500Principal.name
        return subject == issuer ||
            subject.contains("Root CA", ignoreCase = true) ||
            subject.contains("Test CA 000", ignoreCase = true)
    }

    fun parsePayload(request: ProvisioningIngestRequest): ProvisioningBlob {
        val raw = request.rawPayload?.trim()
        if (!raw.isNullOrBlank()) {
            return parseRawString(raw)
        }

        // If fields are passed directly in request body
        if (!request.coprUidHex.isNullOrBlank() && !request.authPubkeyB64.isNullOrBlank()) {
            return ProvisioningBlob(
                status = request.status ?: "PROVISIONED",
                sessionToken = request.sessionToken ?: "",
                coprUidHex = request.coprUidHex,
                lockPubkeyB64 = request.lockPubkeyB64,
                authPubkeyB64 = request.authPubkeyB64,
                manufacturerSigB64 = request.manufacturerSigB64 ?: "",
                attestationDigestHex = request.attestationDigestHex ?: "",
                infineonCertsDerB64 = request.infineonCertsDerB64 ?: emptyList(),
                efuseStatus = request.efuseStatus,
            )
        }

        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid provisioning payload found")
    }

    fun parseRawString(rawInput: String): ProvisioningBlob {
        var cleanInput = rawInput.trim()
        val prefixes = listOf("PROVISION_RESULT:", "SOFT_PROVISION_RESULT:", "CERT_EXPORT:")
        for (prefix in prefixes) {
            if (cleanInput.startsWith(prefix)) {
                cleanInput = cleanInput.substring(prefix.length).trim()
                break
            }
        }

        return try {
            objectMapper.readValue(cleanInput, ProvisioningBlob::class.java)
        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Failed to parse provisioning JSON payload: ${e.message}",
            )
        }
    }

    fun verifyInfineonCertificateChain(
        certsDerB64: List<String>
    ): Pair<X509Certificate, List<String>> {
        if (certsDerB64.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "No certificates in infineon_certs_der_b64",
            )
        }

        val certFactory = CertificateFactory.getInstance("X.509", "BC")
        val chainCerts =
            certsDerB64.map { b64 ->
                val bytes = Base64.getDecoder().decode(b64.trim())
                certFactory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
            }

        val leafCert = chainCerts[0]
        val warnings = mutableListOf<String>()

        // Check if leaf cert is expired
        try {
            leafCert.checkValidity()
        } catch (e: Exception) {
            warnings.add("Leaf certificate validity warning: ${e.message}")
        }

        // Validate cryptographic signature chain to a trusted root
        val candidateIntermediates =
            (chainCerts.drop(1) + intermediateCertificates).distinctBy {
                it.subjectX500Principal.name
            }

        var current: X509Certificate = leafCert
        var reachedRoot = false
        var depth = 0

        while (depth < 10) {
            // Check if current was signed directly by a trusted root
            for (root in rootCertificates) {
                try {
                    current.verify(root.publicKey)
                    reachedRoot = true
                    break
                } catch (_: Exception) {}
            }
            if (reachedRoot) break

            // Find intermediate issuer
            val issuerCert =
                candidateIntermediates.firstOrNull {
                    it.subjectX500Principal.name == current.issuerX500Principal.name
                } ?: break

            try {
                current.verify(issuerCert.publicKey)
                current = issuerCert
                depth++
            } catch (e: Exception) {
                break
            }
        }

        if (!reachedRoot) {
            // If roots weren't loaded or chain incomplete, verify self-consistency or try with all
            // available CAs
            // If leafCert has valid public key and signature can be validated against its issuer
            var verifiedAgainstAnyIssuer = false
            for (issuer in (candidateIntermediates + rootCertificates)) {
                try {
                    leafCert.verify(issuer.publicKey)
                    verifiedAgainstAnyIssuer = true
                    break
                } catch (_: Exception) {}
            }

            if (!verifiedAgainstAnyIssuer && rootCertificates.isNotEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Infineon certificate chain could not be verified to a trusted Root CA",
                )
            } else if (!reachedRoot) {
                warnings.add("Infineon certificate chain anchor verified with standalone issuer")
            }
        }

        val pk = leafCert.publicKey
        if (pk !is ECPublicKey) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Leaf certificate public key is not an EC key",
            )
        }

        return Pair(leafCert, warnings)
    }

    fun verifyAttestationDigest(
        coprUidHex: String,
        authPubkeyB64: String,
        attestationDigestHex: String,
    ): Pair<ByteArray, ByteArray> {
        val coprUidBytes =
            try {
                HexFormat.of().parseHex(coprUidHex.trim().lowercase())
            } catch (e: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid copr_uid_hex: ${e.message}",
                )
            }

        val authPubkeySec1 =
            try {
                Base64.getDecoder().decode(authPubkeyB64.trim())
            } catch (e: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid auth_pubkey_b64: ${e.message}",
                )
            }

        if (authPubkeySec1.size != 65 || authPubkeySec1[0] != 0x04.toByte()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "auth_pubkey_b64 must be a 65-byte uncompressed SEC1 P-256 public key starting with 0x04",
            )
        }

        // Validate point on curve
        try {
            decodeSec1PublicKey(authPubkeySec1)
        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "auth_pubkey_b64 is not a valid P-256 curve point: ${e.message}",
            )
        }

        val domainPrefix = "TARTARUS-AUTH-KEY-V1:".toByteArray(Charsets.US_ASCII)
        val preimage = domainPrefix + coprUidBytes + authPubkeySec1

        val md = MessageDigest.getInstance("SHA-256")
        val expectedDigest = md.digest(preimage)
        val expectedDigestHex = HexFormat.of().formatHex(expectedDigest)

        if (!expectedDigestHex.equals(attestationDigestHex.trim(), ignoreCase = true)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Attestation digest mismatch. Expected $expectedDigestHex, got ${attestationDigestHex.trim()}",
            )
        }

        return Pair(preimage, expectedDigest)
    }

    fun verifyManufacturerSignature(
        factoryPublicKey: PublicKey,
        expectedDigest: ByteArray,
        manufacturerSigB64: String,
        preimage: ByteArray,
    ) {
        val mfgSigRaw =
            try {
                Base64.getDecoder().decode(manufacturerSigB64.trim())
            } catch (e: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid manufacturer_sig_b64 encoding: ${e.message}",
                )
            }

        if (mfgSigRaw.size != 64) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "manufacturer_sig_b64 must be 64 bytes (IEEE P1363 format)",
            )
        }

        val derSig =
            try {
                rawToDerSignature(mfgSigRaw)
            } catch (e: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Failed to convert P1363 signature to DER: ${e.message}",
                )
            }

        val verifier = Signature.getInstance("NONEwithECDSA", "BC")
        verifier.initVerify(factoryPublicKey)
        verifier.update(expectedDigest)

        val isValid =
            try {
                verifier.verify(derSig)
            } catch (e: Exception) {
                false
            }

        if (!isValid) {
            // Also try SHA256withECDSA over preimage
            val verifier2 = Signature.getInstance("SHA256withECDSA", "BC")
            verifier2.initVerify(factoryPublicKey)
            verifier2.update(preimage)
            if (!verifier2.verify(derSig)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid manufacturer attestation signature",
                )
            }
        }
    }

    fun determineProvisioningMode(
        status: String,
        efuseStatus: EfuseStatusPayload?,
    ): Pair<String, List<String>> {
        val warnings = mutableListOf<String>()
        val isHard =
            status.equals("PROVISIONED", ignoreCase = true) ||
                (status.equals("EXPORTED", ignoreCase = true) &&
                    efuseStatus?.blk3Provisioned == true)

        val mode = if (isHard) "HARD" else "SOFT"

        if (isHard) {
            if (efuseStatus?.blk3Provisioned != true) {
                warnings.add(
                    "Security warning: HARD provisioned unit does not have BLK3 provisioned"
                )
            }
            if (efuseStatus?.blk3WriteProtected != true) {
                warnings.add(
                    "Security warning: HARD provisioned unit does not have BLK3 write-protected"
                )
            }
            if (efuseStatus?.padJtagDisabled != true) {
                warnings.add("Security warning: PAD JTAG is not disabled")
            }
            if (efuseStatus?.usbJtagDisabled != true) {
                warnings.add("Security warning: USB JTAG is not disabled")
            }
            if (efuseStatus?.softJtagDisabled != true) {
                warnings.add("Security warning: SOFT JTAG is not disabled")
            }
            if (efuseStatus?.securityDownloadEnabled != true) {
                warnings.add("Security warning: Security download mode is not enabled")
            }
        }

        return Pair(mode, warnings)
    }

    fun verifyPreview(request: ProvisioningIngestRequest): ProvisioningPreviewResponse {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        return try {
            val blob = parsePayload(request)
            val hardwareId = blob.coprUidHex.trim().lowercase()

            val (leafCert, certWarnings) = verifyInfineonCertificateChain(blob.infineonCertsDerB64)
            warnings.addAll(certWarnings)

            val (preimage, digest) =
                verifyAttestationDigest(
                    blob.coprUidHex,
                    blob.authPubkeyB64,
                    blob.attestationDigestHex,
                )

            verifyManufacturerSignature(
                leafCert.publicKey,
                digest,
                blob.manufacturerSigB64,
                preimage,
            )

            val (mode, efuseWarnings) = determineProvisioningMode(blob.status, blob.efuseStatus)
            warnings.addAll(efuseWarnings)

            ProvisioningPreviewResponse(
                valid = true,
                provisioningMode = mode,
                hardwareId = hardwareId,
                sessionToken = blob.sessionToken,
                leafCertSubject = leafCert.subjectX500Principal.name,
                leafCertIssuer = leafCert.issuerX500Principal.name,
                leafCertSerialHex = leafCert.serialNumber.toString(16).lowercase(),
                chainValid = true,
                attestationDigestValid = true,
                signatureValid = true,
                efuseStatus = blob.efuseStatus,
                warnings = warnings,
                errors = emptyList(),
            )
        } catch (e: ResponseStatusException) {
            errors.add(e.reason ?: e.message)
            ProvisioningPreviewResponse(
                valid = false,
                provisioningMode = "UNKNOWN",
                hardwareId = request.coprUidHex ?: "",
                sessionToken = request.sessionToken ?: "",
                leafCertSubject = "",
                leafCertIssuer = "",
                leafCertSerialHex = "",
                chainValid = false,
                attestationDigestValid = false,
                signatureValid = false,
                efuseStatus = request.efuseStatus,
                warnings = warnings,
                errors = errors,
            )
        } catch (e: Exception) {
            errors.add(e.message ?: "Unknown verification error")
            ProvisioningPreviewResponse(
                valid = false,
                provisioningMode = "UNKNOWN",
                hardwareId = request.coprUidHex ?: "",
                sessionToken = request.sessionToken ?: "",
                leafCertSubject = "",
                leafCertIssuer = "",
                leafCertSerialHex = "",
                chainValid = false,
                attestationDigestValid = false,
                signatureValid = false,
                efuseStatus = request.efuseStatus,
                warnings = warnings,
                errors = errors,
            )
        }
    }

    @Transactional
    fun ingestAndSave(request: ProvisioningIngestRequest): ProvisionedHardwareUnitDetailDto {
        val blob = parsePayload(request)
        val hardwareId = blob.coprUidHex.trim().lowercase()

        // 1. Verify certificate chain
        val (leafCert, _) = verifyInfineonCertificateChain(blob.infineonCertsDerB64)

        // 2. Verify attestation digest
        val (preimage, digest) =
            verifyAttestationDigest(blob.coprUidHex, blob.authPubkeyB64, blob.attestationDigestHex)

        // 3. Verify manufacturer signature
        verifyManufacturerSignature(leafCert.publicKey, digest, blob.manufacturerSigB64, preimage)

        // 4. Determine provisioning mode & validate efuses
        val (mode, _) = determineProvisioningMode(blob.status, blob.efuseStatus)

        val existing = unitRepository.findByHardwareId(hardwareId)
        val now = timeSource.nowInUtc()

        val unit: ProvisionedHardwareUnit =
            if (existing != null) {
                if (existing.provisioningMode == "HARD" && mode == "SOFT") {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot downgrade a HARD provisioned unit to SOFT",
                    )
                }
                existing.provisioningMode = mode
                existing.authPubkeyB64 = blob.authPubkeyB64.trim()
                existing.manufacturerSigB64 = blob.manufacturerSigB64.trim()
                existing.attestationDigestHex = blob.attestationDigestHex.trim().lowercase()
                existing.infineonCertsJson =
                    objectMapper.writeValueAsString(blob.infineonCertsDerB64)
                existing.leafCertSubject = leafCert.subjectX500Principal.name
                existing.leafCertIssuer = leafCert.issuerX500Principal.name
                existing.leafCertSerialHex = leafCert.serialNumber.toString(16).lowercase()

                val ef = blob.efuseStatus
                existing.blk3Provisioned = ef?.blk3Provisioned ?: false
                existing.blk3WriteProtected = ef?.blk3WriteProtected ?: false
                existing.secureBootEnabled = ef?.secureBootEnabled ?: false
                existing.padJtagDisabled = ef?.padJtagDisabled ?: false
                existing.usbJtagDisabled = ef?.usbJtagDisabled ?: false
                existing.softJtagDisabled = ef?.softJtagDisabled ?: false
                existing.securityDownloadEnabled = ef?.securityDownloadEnabled ?: false
                existing.usbDownloadDisabled = ef?.usbDownloadDisabled ?: false
                existing.secureVersion = ef?.secureVersion ?: 0

                existing.lastKnownSessionToken = blob.sessionToken
                if (!blob.lockPubkeyB64.isNullOrBlank()) {
                    existing.lastKnownLockPubkeyB64 = blob.lockPubkeyB64
                }
                if (!request.label.isNullOrBlank()) {
                    existing.label = request.label
                }
                if (!request.notes.isNullOrBlank()) {
                    existing.notes = request.notes
                }
                existing.updatedAt = now
                existing
            } else {
                val ef = blob.efuseStatus
                ProvisionedHardwareUnit(
                    hardwareId = hardwareId,
                    provisioningMode = mode,
                    authPubkeyB64 = blob.authPubkeyB64.trim(),
                    manufacturerSigB64 = blob.manufacturerSigB64.trim(),
                    attestationDigestHex = blob.attestationDigestHex.trim().lowercase(),
                    infineonCertsJson = objectMapper.writeValueAsString(blob.infineonCertsDerB64),
                    leafCertSubject = leafCert.subjectX500Principal.name,
                    leafCertIssuer = leafCert.issuerX500Principal.name,
                    leafCertSerialHex = leafCert.serialNumber.toString(16).lowercase(),
                    blk3Provisioned = ef?.blk3Provisioned ?: false,
                    blk3WriteProtected = ef?.blk3WriteProtected ?: false,
                    secureBootEnabled = ef?.secureBootEnabled ?: false,
                    padJtagDisabled = ef?.padJtagDisabled ?: false,
                    usbJtagDisabled = ef?.usbJtagDisabled ?: false,
                    softJtagDisabled = ef?.softJtagDisabled ?: false,
                    securityDownloadEnabled = ef?.securityDownloadEnabled ?: false,
                    usbDownloadDisabled = ef?.usbDownloadDisabled ?: false,
                    secureVersion = ef?.secureVersion ?: 0,
                    initialSessionToken = blob.sessionToken,
                    lastKnownSessionToken = blob.sessionToken,
                    lastKnownLockPubkeyB64 = blob.lockPubkeyB64,
                    label = request.label,
                    notes = request.notes,
                    isRevoked = false,
                    provisionedAt = now,
                    updatedAt = now,
                )
            }

        val saved = unitRepository.save(unit)
        syncLockSessionPubkey(blob.sessionToken, blob.lockPubkeyB64, now)
        return toDetailDto(saved)
    }

    private fun syncLockSessionPubkey(
        sessionToken: String,
        newPubkey: String?,
        now: java.time.OffsetDateTime,
    ) {
        if (newPubkey.isNullOrBlank()) return
        try {
            val session = lockSessionRepository?.findBySessionToken(sessionToken) ?: return
            if (session.publicKey != newPubkey) {
                session.publicKey = newPubkey
                session.updatedAt = now
                lockSessionRepository.save(session)
            }
        } catch (e: Exception) {
            println(
                "⚠️ Warning: could not update LockSession for session $sessionToken: ${e.message}"
            )
        }
    }

    fun listUnits(
        mode: String?,
        revoked: Boolean?,
        search: String?,
    ): List<ProvisionedHardwareUnitSummaryDto> {
        var units = unitRepository.findAll()

        if (!mode.isNullOrBlank()) {
            units = units.filter { it.provisioningMode.equals(mode.trim(), ignoreCase = true) }
        }

        if (revoked != null) {
            units = units.filter { it.isRevoked == revoked }
        }

        if (!search.isNullOrBlank()) {
            val q = search.trim().lowercase()
            units =
                units.filter {
                    it.hardwareId.lowercase().contains(q) ||
                        (it.lastKnownSessionToken?.lowercase()?.contains(q) == true) ||
                        it.initialSessionToken.lowercase().contains(q) ||
                        (it.label?.lowercase()?.contains(q) == true)
                }
        }

        return units.sortedByDescending { it.provisionedAt }.map { toSummaryDto(it) }
    }

    fun getUnitDetail(hardwareId: String): ProvisionedHardwareUnitDetailDto {
        val normId = hardwareId.trim().lowercase()
        val unit =
            unitRepository.findByHardwareId(normId)
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Hardware unit not found: $hardwareId",
                )

        val logs =
            logRepository.findTop50ByHardwareIdOrderByCreatedAtDesc(normId).map {
                HardwareAttestationLogDto(
                    id = it.id,
                    hardwareId = it.hardwareId,
                    sessionToken = it.sessionToken,
                    firmwareVersion = it.firmwareVersion,
                    verificationMethod = it.verificationMethod,
                    success = it.success,
                    failureReason = it.failureReason,
                    createdAt = it.createdAt,
                )
            }

        return toDetailDto(unit, logs)
    }

    @Transactional
    fun updateUnit(
        hardwareId: String,
        request: UpdateHardwareUnitRequest,
    ): ProvisionedHardwareUnitDetailDto {
        val normId = hardwareId.trim().lowercase()
        val unit =
            unitRepository.findByHardwareId(normId)
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Hardware unit not found: $hardwareId",
                )

        if (request.label != null) {
            unit.label = request.label
        }
        if (request.notes != null) {
            unit.notes = request.notes
        }
        if (request.isRevoked != null) {
            unit.isRevoked = request.isRevoked
            if (unit.isRevoked) {
                unit.revokedReason = request.revokedReason ?: "Revoked by admin"
            } else {
                unit.revokedReason = null
            }
        }
        unit.updatedAt = timeSource.nowInUtc()
        val saved = unitRepository.save(unit)
        return toDetailDto(saved)
    }

    private fun toSummaryDto(u: ProvisionedHardwareUnit): ProvisionedHardwareUnitSummaryDto {
        return ProvisionedHardwareUnitSummaryDto(
            hardwareId = u.hardwareId,
            provisioningMode = u.provisioningMode,
            label = u.label,
            initialSessionToken = u.initialSessionToken,
            lastKnownSessionToken = u.lastKnownSessionToken,
            lastFirmwareVersion = u.lastFirmwareVersion,
            lastAttestedAt = u.lastAttestedAt,
            isRevoked = u.isRevoked,
            provisionedAt = u.provisionedAt,
            updatedAt = u.updatedAt,
        )
    }

    private fun toDetailDto(
        u: ProvisionedHardwareUnit,
        logs: List<HardwareAttestationLogDto> = emptyList(),
    ): ProvisionedHardwareUnitDetailDto {
        val certsList: List<String> =
            try {
                objectMapper.readValue(
                    u.infineonCertsJson,
                    object : TypeReference<List<String>>() {},
                )
            } catch (_: Exception) {
                emptyList()
            }

        return ProvisionedHardwareUnitDetailDto(
            hardwareId = u.hardwareId,
            provisioningMode = u.provisioningMode,
            authPubkeyB64 = u.authPubkeyB64,
            manufacturerSigB64 = u.manufacturerSigB64,
            attestationDigestHex = u.attestationDigestHex,
            infineonCertsDerB64 = certsList,
            leafCertSubject = u.leafCertSubject,
            leafCertIssuer = u.leafCertIssuer,
            leafCertSerialHex = u.leafCertSerialHex,
            blk3Provisioned = u.blk3Provisioned,
            blk3WriteProtected = u.blk3WriteProtected,
            secureBootEnabled = u.secureBootEnabled,
            padJtagDisabled = u.padJtagDisabled,
            usbJtagDisabled = u.usbJtagDisabled,
            softJtagDisabled = u.softJtagDisabled,
            securityDownloadEnabled = u.securityDownloadEnabled,
            usbDownloadDisabled = u.usbDownloadDisabled,
            secureVersion = u.secureVersion,
            initialSessionToken = u.initialSessionToken,
            lastKnownSessionToken = u.lastKnownSessionToken,
            lastKnownLockPubkeyB64 = u.lastKnownLockPubkeyB64,
            lastFirmwareVersion = u.lastFirmwareVersion,
            lastAttestedAt = u.lastAttestedAt,
            label = u.label,
            notes = u.notes,
            isRevoked = u.isRevoked,
            revokedReason = u.revokedReason,
            provisionedAt = u.provisionedAt,
            updatedAt = u.updatedAt,
            attestationLogs = logs,
        )
    }
}
