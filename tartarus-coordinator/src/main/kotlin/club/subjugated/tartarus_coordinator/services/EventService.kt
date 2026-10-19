package club.subjugated.tartarus_coordinator.services

import club.subjugated.fb.event.EventType
import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.Event
import club.subjugated.tartarus_coordinator.models.EventDelivery
import club.subjugated.tartarus_coordinator.storage.BotRepository
import club.subjugated.tartarus_coordinator.storage.ContractRepository
import club.subjugated.tartarus_coordinator.storage.EventDeliveryRepository
import club.subjugated.tartarus_coordinator.storage.EventRepository
import club.subjugated.tartarus_coordinator.util.verifySignedEvent
import java.nio.ByteBuffer
import java.time.OffsetDateTime
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

    @Autowired lateinit var eventRepository: EventRepository
    @Autowired lateinit var eventDeliveryRepository: EventDeliveryRepository
    @Autowired lateinit var contractRepository: ContractRepository
    @Autowired lateinit var lockSessionService: LockSessionService
    @Autowired lateinit var botRepository: BotRepository
    @Autowired lateinit var mqttClient: MqttClient

    fun processIncomingLockEvent(sessionToken: String, payloadBytes: ByteArray): Event? {
        val lockSession = lockSessionService.findBySessionToken(sessionToken)
        if (lockSession == null) {
            logger.warn("Received event for unknown lock session: {}", sessionToken)
            return null
        }

        val signedEvent =
            verifySignedEvent(ByteBuffer.wrap(payloadBytes), lockSession.decodePublicKey())
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

        // Fan out to authorized contract bots
        if (contract != null) {
            val embeddedBots = contract.getEmbeddedBots()
            for (embeddedBot in embeddedBots) {
                if (embeddedBot.permissions?.receiveEvents == true) {
                    val botName = embeddedBot.name ?: continue
                    val botEntity = botRepository.findByName(botName)

                    val delivery =
                        EventDelivery(
                            event = savedEvent,
                            bot = botEntity,
                            contract = contract,
                            delivered = false,
                        )
                    eventDeliveryRepository.save(delivery)

                    // Publish to bot's inbox
                    try {
                        val topic = "bots/inbox_events_${botName}"
                        mqttClient.publish(topic, MqttMessage(payloadBytes))
                        delivery.delivered = true
                        delivery.deliveredAt = OffsetDateTime.now()
                        eventDeliveryRepository.save(delivery)
                        logger.info("Fanned out event {} to bot {}", savedEvent.name, botName)
                    } catch (e: Exception) {
                        logger.error("Failed to publish event to bot {}: {}", botName, e.message)
                    }
                }
            }
        }

        return savedEvent
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
