package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.event.EventType
import club.subjugated.fb.event.PeriodicUpdate
import club.subjugated.fb.event.SignedEvent
import club.subjugated.fb.event.StartedUpdate
import club.subjugated.tartarus_coordinator.api.messages.NewLockSessionMessage
import club.subjugated.tartarus_coordinator.components.EventWebSocketHandler
import club.subjugated.tartarus_coordinator.models.BotState
import club.subjugated.tartarus_coordinator.models.CommandState
import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.Event
import club.subjugated.tartarus_coordinator.models.EventDelivery
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.storage.BotRepository
import club.subjugated.tartarus_coordinator.storage.ContractRepository
import club.subjugated.tartarus_coordinator.storage.EventDeliveryRepository
import club.subjugated.tartarus_coordinator.storage.EventRepository
import club.subjugated.tartarus_coordinator.storage.ProvisionedHardwareUnitRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.verifySignedEvent
import club.subjugated.tartarus_coordinator.util.verifySignedEventWithEmbeddedKey
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.ByteBuffer
import java.time.OffsetDateTime
import java.util.Base64
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class EventService {
    private val logger = LoggerFactory.getLogger(EventService::class.java)
    private val objectMapper = ObjectMapper()

    @Autowired lateinit var eventRepository: EventRepository
    @Autowired lateinit var eventDeliveryRepository: EventDeliveryRepository
    @Autowired lateinit var contractRepository: ContractRepository
    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var botRepository: BotRepository
    @Autowired(required = false) var mqttClient: MqttClient? = null
    @Autowired(required = false) var eventWebSocketHandler: EventWebSocketHandler? = null
    @Autowired(required = false) var unitRepository: ProvisionedHardwareUnitRepository? = null
    @Autowired(required = false) var configurationService: ConfigurationService? = null
    @Autowired(required = false) var firmwareService: FirmwareService? = null
    @Autowired(required = false) var timeSource: TimeSource? = null

    fun processIncomingLockEvent(sessionToken: String, payloadBytes: ByteArray): Event? {
        val peekedSignedEvent =
            try {
                SignedEvent.getRootAsSignedEvent(ByteBuffer.wrap(payloadBytes))
            } catch (e: Exception) {
                logger.error("Failed to decode SignedEvent for session {}: {}", sessionToken, e.message)
                return null
            }

        val peekedPayload =
            peekedSignedEvent.payload
                ?: run {
                    logger.error("SignedEvent payload is null for session: {}", sessionToken)
                    return null
                }

        if (peekedPayload.eventType == EventType.StartedUpdate) {
            return handleStartedUpdateEvent(sessionToken, payloadBytes)
        }

        val lockSession = lockSessionService.findBySessionToken(sessionToken)
        if (lockSession == null) {
            logger.warn("Received event for unknown lock session: {}", sessionToken)
            return null
        }

        var signedEvent =
            verifySignedEvent(ByteBuffer.wrap(payloadBytes), lockSession.decodePublicKey())
        if (signedEvent == null) {
            val unit =
                unitRepository?.findByLastKnownSessionToken(sessionToken)?.firstOrNull()
                    ?: unitRepository?.findByInitialSessionToken(sessionToken)?.firstOrNull()
            val unitKey = unit?.lastKnownLockPubkeyB64
            if (unitKey != null && unitKey != lockSession.publicKey) {
                val keyBytes = Base64.getDecoder().decode(unitKey)
                val revalidated = verifySignedEvent(ByteBuffer.wrap(payloadBytes), keyBytes)
                if (revalidated != null) {
                    lockSession.publicKey = unitKey
                    timeSource?.let { lockSession.updatedAt = it.nowInUtc() }
                    lockSessionService.saveLockSession(lockSession)
                    signedEvent = revalidated
                }
            }
        }

        if (signedEvent == null) {
            logger.error("Failed to verify signature on event for session: {}", sessionToken)
            return null
        }

        val eventPayload =
            signedEvent.payload
                ?: run {
                    logger.error("SignedEvent payload is null for session: {}", sessionToken)
                    return null
                }

        val metadata =
            eventPayload.metadata
                ?: run {
                    logger.error("Event metadata is null for session: {}", sessionToken)
                    return null
                }

        val contractSerial = metadata.contractSerialNumber.toInt()
        val counter = metadata.counter.toInt()
        val correlationId = metadata.correlationId

        if (eventPayload.eventType == EventType.PeriodicUpdate) {
            val periodicUpdate = PeriodicUpdate()
            if (eventPayload.event(periodicUpdate) == null) {
                logger.error("PeriodicUpdate union table is null for session: {}", sessionToken)
                return null
            }
            logger.info(
                "Processing PeriodicUpdate: session={}, contractSerial={}, isLocked={}, emergencyUnlocked={}, deviceUptime={}s",
                sessionToken,
                contractSerial,
                periodicUpdate.isLocked,
                periodicUpdate.emergencyUnlocked,
                periodicUpdate.deviceUptime,
            )
            lockSessionService.handlePeriodicUpdate(sessionToken, contractSerial, periodicUpdate)

            fanOutEphemeralEventToContractBots(
                lockSession = lockSession,
                contractSerial = contractSerial,
                eventId = metadata.eventId ?: "$sessionToken:$contractSerial:0",
                eventType = "PeriodicUpdate",
                correlationId = correlationId,
                payloadBytes = payloadBytes,
                extraFields = mapOf("deviceUptime" to periodicUpdate.deviceUptime.toLong()),
            )
            return null
        }

        val eventTypeStr =
            when (eventPayload.eventType) {
                EventType.AcceptContract -> "AcceptContract"
                EventType.LocalLock -> "LocalLock"
                EventType.LocalUnlock -> "LocalUnlock"
                EventType.Lock -> "Lock"
                EventType.Unlock -> "Unlock"
                EventType.ReleaseContract -> "ReleaseContract"
                EventType.EmergencyUnlock -> "EmergencyUnlock"
                else -> "Undefined"
            }

        logger.info(
            "Processing event: session={}, contractSerial={}, counter={}, type={}",
            sessionToken,
            contractSerial,
            counter,
            eventTypeStr,
        )

        // Update LockSession state on physical state-transition events
        when (eventPayload.eventType) {
            EventType.AcceptContract,
            EventType.LocalLock,
            EventType.Lock -> {
                lockSession.isLocked = true
                lockSessionService.saveLockSession(lockSession)
            }
            EventType.LocalUnlock,
            EventType.Unlock -> {
                lockSession.isLocked = false
                lockSessionService.saveLockSession(lockSession)
            }
            EventType.EmergencyUnlock -> {
                lockSession.isLocked = false
                lockSession.emergencyUnlocked = true
                lockSessionService.saveLockSession(lockSession)
            }
            EventType.ReleaseContract -> {
                lockSession.isLocked = false
                lockSession.emergencyUnlocked = false
                lockSessionService.saveLockSession(lockSession)
            }
        }

        val contract =
            contractRepository.findByLockSessionIdAndSerialNumber(lockSession.id, contractSerial)

        // Deduplication check: if (contract, counter) or (session, serial, counter) already exists
        val existingEvent =
            if (contract != null) {
                eventRepository.findByContractIdAndCounter(contract.id, counter)
            } else {
                eventRepository.findBySessionTokenAndContractSerialNumberAndCounter(
                    sessionToken,
                    contractSerial,
                    counter,
                )
            }

        if (existingEvent != null) {
            logger.info(
                "Duplicate event received (id={}, counter={}), skipping creation",
                existingEvent.id,
                counter,
            )
            return existingEvent
        }

        val event =
            Event(
                sessionToken = sessionToken,
                contractSerialNumber = contractSerial,
                counter = counter,
                eventType = eventTypeStr,
                correlationId = correlationId,
                body = payloadBytes,
                lockSession = lockSession,
                contract = contract,
            )
        val savedEvent = eventRepository.save(event)

        // Fan out to authorized contract bots over WebSocket
        if (contract != null) {
            fanOutEventToContractBots(contract, savedEvent, payloadBytes)
        }

        return savedEvent
    }

    private fun handleStartedUpdateEvent(sessionToken: String, payloadBytes: ByteArray): Event? {
        val signedEvent = verifySignedEventWithEmbeddedKey(ByteBuffer.wrap(payloadBytes))
        if (signedEvent == null) {
            logger.error(
                "Failed to verify signature on StartedUpdate event for session: {}",
                sessionToken,
            )
            return null
        }

        val eventPayload = signedEvent.payload ?: return null
        val metadata = eventPayload.metadata
        val startedUpdate = StartedUpdate()
        if (eventPayload.event(startedUpdate) == null) {
            logger.error("StartedUpdate union table is null for session: {}", sessionToken)
            return null
        }

        val effectiveSessionToken = metadata?.lockSession ?: sessionToken
        val contractSerial = metadata?.contractSerialNumber?.toInt() ?: 0
        val keyBytes =
            ByteArray(startedUpdate.publicKeyLength) { startedUpdate.publicKey(it).toByte() }
        val pubKeyB64 = Base64.getEncoder().encodeToString(keyBytes)

        val nlsm =
            NewLockSessionMessage(
                publicKey = pubKeyB64,
                sessionToken = effectiveSessionToken,
                userSessionPublicKey = "",
            )

        val lockSession = lockSessionService.createLockSession(nlsm)
        lockSession.isLocked = startedUpdate.isLocked
        lockSessionService.saveLockSession(lockSession)

        try {
            val matched =
                (unitRepository?.findByLastKnownSessionToken(effectiveSessionToken) ?: emptyList()) +
                    (unitRepository?.findByInitialSessionToken(effectiveSessionToken)
                        ?: emptyList())
            for (u in matched.distinctBy { it.hardwareId }) {
                u.lastKnownLockPubkeyB64 = pubKeyB64
                u.lastKnownSessionToken = effectiveSessionToken
                unitRepository?.save(u)
            }
        } catch (e: Exception) {
            logger.warn(
                "Could not update provisioned unit for session {}: {}",
                effectiveSessionToken,
                e.message,
            )
        }

        val queue = lockSession.commandQueue.firstOrNull()
        if (queue != null && mqttClient != null) {
            val pendingCommands = queue.commands.filter { it.state == CommandState.PENDING }
            for (command in pendingCommands) {
                mqttClient?.publish("locks/$effectiveSessionToken", MqttMessage(command.body))
            }
        }

        if (configurationService != null && mqttClient != null) {
            val configData = configurationService!!.getConfigurationAsFB()
            mqttClient?.publish("configuration/$effectiveSessionToken", MqttMessage(configData))
        }

        if (firmwareService != null && mqttClient != null) {
            val firmwareChallenge =
                firmwareService!!.generateFirmwareChallenge(effectiveSessionToken)
            mqttClient?.publish("firmware/$effectiveSessionToken", MqttMessage(firmwareChallenge))
        }

        fanOutEphemeralEventToContractBots(
            lockSession = lockSession,
            contractSerial = contractSerial,
            eventId = metadata?.eventId ?: "$effectiveSessionToken:$contractSerial:0",
            eventType = "StartedUpdate",
            correlationId = metadata?.correlationId,
            payloadBytes = payloadBytes,
        )

        return null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun fanOutEphemeralEventToContractBots(
        lockSession: LockSession,
        contractSerial: Int,
        eventId: String,
        eventType: String,
        correlationId: String?,
        payloadBytes: ByteArray,
        extraFields: Map<String, Any?> = emptyMap(),
    ) {
        val contract =
            if (contractSerial != 0) {
                contractRepository.findByLockSessionIdAndSerialNumber(
                    lockSession.id,
                    contractSerial,
                )
            } else {
                null
            } ?: return

        val embeddedBots =
            contract.getEmbeddedBots().filter {
                it.permissions?.receiveEvents == true && !it.name.isNullOrBlank()
            }
        val rawSignedEventB64 = Base64.getEncoder().encodeToString(payloadBytes)
        for (embeddedBot in embeddedBots) {
            val botName = embeddedBot.name!!
            val botEntity = botRepository.findByName(botName)
            if (botEntity.state != BotState.ACTIVE) {
                logger.info("Skipping ephemeral event fanout to non-active bot {}", botName)
                continue
            }
            try {
                val payloadMap =
                    mutableMapOf<String, Any?>(
                        "type" to "bot_event",
                        "eventId" to eventId,
                        "botName" to botEntity.name,
                        "contractName" to contract.name,
                        "contractSerial" to contractSerial,
                        "lockSession" to (lockSession.sessionToken ?: ""),
                        "counter" to 0,
                        "eventType" to eventType,
                        "correlationId" to correlationId,
                        "rawSignedEventBase64" to rawSignedEventB64,
                    )
                payloadMap.putAll(extraFields)
                val wsPayload = objectMapper.writeValueAsString(payloadMap)
                val sent =
                    eventWebSocketHandler?.sendBotMessage(
                        botEntity.name,
                        botEntity.authorSession?.name,
                        wsPayload,
                    ) ?: false
                if (sent) {
                    logger.info(
                        "Opportunistically fanned out ephemeral event {} ({}) to bot {} over WebSocket",
                        eventType,
                        eventId,
                        botEntity.name,
                    )
                }
            } catch (e: Exception) {
                logger.error(
                    "Failed to deliver ephemeral WebSocket event {} to bot {}: {}",
                    eventType,
                    botEntity.name,
                    e.message,
                )
            }
        }
    }

    private fun fanOutEventToContractBots(
        contract: Contract,
        savedEvent: Event,
        payloadBytes: ByteArray,
    ) {
        val embeddedBots =
            contract.getEmbeddedBots().filter {
                it.permissions?.receiveEvents == true && !it.name.isNullOrBlank()
            }
        val rawSignedEventB64 = Base64.getEncoder().encodeToString(payloadBytes)
        for (embeddedBot in embeddedBots) {
            val botName = embeddedBot.name!!
            val botEntity = botRepository.findByName(botName)
            if (botEntity.state != BotState.ACTIVE) {
                logger.info("Skipping event fanout to non-active bot {}", botName)
            } else {
                deliverEventToSingleBot(contract, savedEvent, botEntity, rawSignedEventB64)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun deliverEventToSingleBot(
        contract: Contract,
        savedEvent: Event,
        botEntity: club.subjugated.tartarus_coordinator.models.Bot,
        rawSignedEventB64: String,
    ) {
        val delivery =
            EventDelivery(
                event = savedEvent,
                bot = botEntity,
                contract = contract,
                delivered = false,
            )
        eventDeliveryRepository.save(delivery)

        try {
            val wsPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "type" to "bot_event",
                        "eventId" to savedEvent.name,
                        "botName" to botEntity.name,
                        "contractName" to contract.name,
                        "contractSerial" to savedEvent.contractSerialNumber,
                        "lockSession" to savedEvent.sessionToken,
                        "counter" to savedEvent.counter,
                        "eventType" to savedEvent.eventType,
                        "correlationId" to savedEvent.correlationId,
                        "rawSignedEventBase64" to rawSignedEventB64,
                    )
                )
            val sent =
                eventWebSocketHandler?.sendBotMessage(
                    botEntity.name,
                    botEntity.authorSession?.name,
                    wsPayload,
                ) ?: false
            if (sent) {
                delivery.delivered = true
                delivery.deliveredAt = OffsetDateTime.now()
                eventDeliveryRepository.save(delivery)
                logger.info(
                    "Fanned out event {} to bot {} over WebSocket",
                    savedEvent.name,
                    botEntity.name,
                )
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to deliver WebSocket event to bot {}: {}",
                botEntity.name,
                e.message,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getMissedEventsForBot(
        botName: String,
        contractName: String,
        sinceCounter: Int,
    ): List<EventDelivery> {
        return eventDeliveryRepository.findMissedEventsForBot(botName, contractName, sinceCounter)
    }

    @Transactional(readOnly = true)
    fun findByName(name: String): Event? {
        return eventRepository.findByName(name)
    }

    @Transactional(readOnly = true)
    fun getEventsForContract(contract: Contract): List<Event> {
        val byContractId = eventRepository.findByContractIdOrderByCounterAsc(contract.id)
        if (byContractId.isNotEmpty()) {
            return byContractId
        }
        val sessionToken = contract.lockSession.sessionToken ?: ""
        return eventRepository.findBySessionTokenAndContractSerialNumberOrderByCounterAsc(
            sessionToken,
            contract.serialNumber,
        )
    }
}
