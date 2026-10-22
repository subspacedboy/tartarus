package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.message.firmware.FirmwareChallengeRequest
import club.subjugated.fb.message.firmware.FirmwareMessage
import club.subjugated.fb.message.firmware.MessagePayload
import club.subjugated.tartarus_coordinator.events.FirmwareValidationEvent
import club.subjugated.tartarus_coordinator.models.Firmware
import club.subjugated.tartarus_coordinator.models.FirmwareState
import club.subjugated.tartarus_coordinator.models.FirmwareUpgradePath
import club.subjugated.tartarus_coordinator.models.HardwareAttestationLog
import club.subjugated.tartarus_coordinator.storage.FirmwareRepository
import club.subjugated.tartarus_coordinator.storage.FirmwareUpgradePathRepository
import club.subjugated.tartarus_coordinator.storage.HardwareAttestationLogRepository
import club.subjugated.tartarus_coordinator.storage.ProvisionedHardwareUnitRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.decodeSec1PublicKey
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import club.subjugated.tartarus_coordinator.util.rawToDerSignature
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.flatbuffers.FlatBufferBuilder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.util.*
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class FirmwareService(
    private var firmwareRepository: FirmwareRepository,
    private var firmwareUpgradePathRepository: FirmwareUpgradePathRepository,
    private var timeSource: TimeSource,
    private var publisher: ApplicationEventPublisher,
    @Autowired(required = false)
    private var unitRepository: ProvisionedHardwareUnitRepository? = null,
    @Autowired(required = false)
    private var attestationLogRepository: HardwareAttestationLogRepository? = null,
) {
    @Value("\${tartarus.firmware.challenge_key}") var challengeKey: String = ""

    private val nonceChallenges: Cache<Long, ByteArray> =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10_000).build()

    private val firmwareImages: Cache<String, ByteArray> =
        Caffeine.newBuilder().maximumSize(2).build()

    fun createNewFirmware(image: ByteArray): Firmware {
        if (image[0] == 0xE9.toByte()) {
            println("Magic byte matches")
        } else {
            throw IllegalArgumentException("Firmware doesn't start with magic byte 0xE9")
        }

        val versionBytes = image.copyOfRange(48, 80)
        val version =
            versionBytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(image)
        val hash = Base64.getEncoder().encodeToString(digest.digest())

        val firmware: Firmware =
            firmwareRepository.findFirstByDigest(hash)
                ?: run {
                    val f =
                        Firmware(
                            state = FirmwareState.ACTIVE,
                            image = image,
                            signature = "",
                            digest = hash,
                            version = version,
                            createdAt = timeSource.nowInUtc(),
                        )
                    firmwareRepository.save(f)
                }
        return firmware
    }

    fun getLatest(): Firmware {
        return firmwareRepository.findFirstByOrderByCreatedAtDesc()
    }

    fun getByVersion(versionName: String): List<Firmware> {
        return firmwareRepository.findByVersion(versionName)
    }

    fun getFirmwareBytes(name: String, size: Int, offset: Int): ByteArray {
        val image =
            firmwareImages.get(name) {
                val firmware = firmwareRepository.findByName(name)
                firmware.image!!
            }

        if (offset > image.size) {
            throw IllegalArgumentException("Requested beyond the bounds of the image")
        }
        var end = offset + size
        if (end > image.lastIndex) {
            end = image.lastIndex
        }

        // +1 because copyOfRange is end-exclusive
        return image.copyOfRange(offset, end + 1)
    }

    fun generateFirmwareChallenge(sessionToken: String): ByteArray {
        val builder = FlatBufferBuilder(1024)

        val nonce = ByteArray(32) // P-256 (secp256r1) requires 32 bytes (256 bits)
        SecureRandom().nextBytes(nonce)

        val nonceOffset = builder.createByteVector(nonce)
        FirmwareChallengeRequest.startFirmwareChallengeRequest(builder)
        FirmwareChallengeRequest.addNonce(builder, nonceOffset)
        val challengeOffset = FirmwareChallengeRequest.endFirmwareChallengeRequest(builder)

        val sessionTokenOffset = builder.createString(sessionToken)

        FirmwareMessage.startFirmwareMessage(builder)
        FirmwareMessage.addPayload(builder, challengeOffset)
        FirmwareMessage.addPayloadType(builder, MessagePayload.FirmwareChallengeRequest)
        FirmwareMessage.addSessionToken(builder, sessionTokenOffset)
        val requestId = SecureRandom().nextLong()
        nonceChallenges.put(requestId, nonce)
        FirmwareMessage.addRequestId(builder, requestId)
        val firmwareOffset = FirmwareMessage.endFirmwareMessage(builder)

        builder.finish(firmwareOffset)
        return builder.sizedByteArray()
    }

    fun validateChallengeSignature(
        requestId: Long,
        sessionToken: String,
        data: ByteArray,
        currentFirmware: String? = null,
        hardwareId: String? = null,
    ): Boolean {
        val nonce =
            nonceChallenges.getIfPresent(requestId)
                ?: run {
                    println("⚠️ Challenge nonce for requestId $requestId expired or not found")
                    val event =
                        FirmwareValidationEvent(
                            this,
                            sessionToken = sessionToken,
                            validated = false,
                            currentFirmware = currentFirmware,
                        )
                    publisher.publishEvent(event)
                    return false
                }
        nonceChallenges.invalidate(requestId)

        val signature = rawToDerSignature(data)

        // Case A: hardwareId is present (OPTIGA Provisioned Unit)
        if (!hardwareId.isNullOrBlank()) {
            val normHwId = hardwareId.trim().lowercase()
            val unit = unitRepository?.findByHardwareId(normHwId)

            if (unit == null) {
                logAttestation(
                    hardwareId = normHwId,
                    sessionToken = sessionToken,
                    firmwareVersion = currentFirmware,
                    verificationMethod = "FAILED",
                    success = false,
                    failureReason = "Unregistered hardware_id: $normHwId",
                )
                val event =
                    FirmwareValidationEvent(
                        this,
                        sessionToken = sessionToken,
                        validated = false,
                        currentFirmware = currentFirmware,
                    )
                publisher.publishEvent(event)
                return false
            }

            if (unit.isRevoked) {
                logAttestation(
                    hardwareId = normHwId,
                    sessionToken = sessionToken,
                    firmwareVersion = currentFirmware,
                    verificationMethod = "FAILED",
                    success = false,
                    failureReason = "Hardware unit is revoked",
                )
                val event =
                    FirmwareValidationEvent(
                        this,
                        sessionToken = sessionToken,
                        validated = false,
                        currentFirmware = currentFirmware,
                    )
                publisher.publishEvent(event)
                return false
            }

            val authKey = decodeSec1PublicKey(Base64.getDecoder().decode(unit.authPubkeyB64))
            val verifier =
                Signature.getInstance("NONEwithECDSA", "BC").apply {
                    initVerify(authKey)
                    update(nonce)
                }
            val validated =
                try {
                    verifier.verify(signature)
                } catch (e: Exception) {
                    false
                }

            if (validated) {
                unit.lastKnownSessionToken = sessionToken
                unit.lastFirmwareVersion = currentFirmware
                unit.lastAttestedAt = timeSource.nowInUtc()
                unit.updatedAt = timeSource.nowInUtc()
                unitRepository?.save(unit)

                logAttestation(
                    hardwareId = normHwId,
                    sessionToken = sessionToken,
                    firmwareVersion = currentFirmware,
                    verificationMethod = "OPTIGA_${unit.provisioningMode}",
                    success = true,
                    failureReason = null,
                )

                val event =
                    FirmwareValidationEvent(
                        this,
                        sessionToken = sessionToken,
                        validated = true,
                        currentFirmware = currentFirmware,
                    )
                publisher.publishEvent(event)
                return true
            } else {
                logAttestation(
                    hardwareId = normHwId,
                    sessionToken = sessionToken,
                    firmwareVersion = currentFirmware,
                    verificationMethod = "FAILED",
                    success = false,
                    failureReason = "Signature verification failed against auth_pubkey",
                )

                val event =
                    FirmwareValidationEvent(
                        this,
                        sessionToken = sessionToken,
                        validated = false,
                        currentFirmware = currentFirmware,
                    )
                publisher.publishEvent(event)
                return false
            }
        }

        // Case B: hardwareId is Null / Empty (Legacy Software Key Fallback)
        val challengeKey =
            getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(challengeKey))

        val verifier =
            Signature.getInstance("NONEwithECDSA", "BC").apply {
                initVerify(challengeKey)
                update(nonce)
            }

        val validated =
            try {
                verifier.verify(signature)
            } catch (e: Exception) {
                false
            }

        logAttestation(
            hardwareId = null,
            sessionToken = sessionToken,
            firmwareVersion = currentFirmware,
            verificationMethod = if (validated) "LEGACY_KEY" else "FAILED",
            success = validated,
            failureReason = if (validated) null else "Legacy key signature verification failed",
        )

        val event =
            FirmwareValidationEvent(
                this,
                sessionToken = sessionToken,
                validated = validated,
                currentFirmware = currentFirmware,
            )
        publisher.publishEvent(event)

        return validated
    }

    private fun logAttestation(
        hardwareId: String?,
        sessionToken: String,
        firmwareVersion: String?,
        verificationMethod: String,
        success: Boolean,
        failureReason: String?,
    ) {
        try {
            val log =
                HardwareAttestationLog(
                    hardwareId = hardwareId,
                    sessionToken = sessionToken,
                    firmwareVersion = firmwareVersion,
                    verificationMethod = verificationMethod,
                    success = success,
                    failureReason = failureReason,
                    createdAt = timeSource.nowInUtc(),
                )
            attestationLogRepository?.save(log)
        } catch (e: Exception) {
            println("⚠️ Warning: failed to save attestation log: ${e.message}")
        }
    }

    fun createUpgradePath(fromVersion: String, toVersion: String): FirmwareUpgradePath {
        val upgradePath =
            FirmwareUpgradePath(
                fromVersion = fromVersion,
                toVersion = toVersion,
                createdAt = timeSource.nowInUtc(),
            )
        firmwareUpgradePathRepository.save(upgradePath)
        return upgradePath
    }

    fun findUpgradesForVersion(fromVersion: String): List<FirmwareUpgradePath> {
        return firmwareUpgradePathRepository.findByFromVersion(fromVersion)
    }
}
