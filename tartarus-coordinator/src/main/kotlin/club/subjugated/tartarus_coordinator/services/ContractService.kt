package club.subjugated.tartarus_coordinator.services

import club.subjugated.tartarus_coordinator.api.messages.NewContractMessage
import club.subjugated.tartarus_coordinator.models.AuthorSession
import club.subjugated.tartarus_coordinator.models.Contract
import club.subjugated.tartarus_coordinator.models.ContractState
import club.subjugated.tartarus_coordinator.models.LockSession
import club.subjugated.tartarus_coordinator.storage.ContractRepository
import club.subjugated.tartarus_coordinator.util.TimeSource
import club.subjugated.tartarus_coordinator.util.ValidatedPayload
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.util.Base64

@Service
class ContractService {
    @Autowired
    lateinit var contractRepository: ContractRepository
    @Autowired
    lateinit var timeSource: TimeSource

    fun saveContract(newContractMessage: NewContractMessage, authorSession : AuthorSession) : Contract {
        val decodedData = Base64.getDecoder().decode(newContractMessage.signedMessage!!)
        val buf = ByteBuffer.wrap(decodedData)

        val maybeContract = signedMessageBytesValidator(buf)
        when(maybeContract) {
            is ValidatedPayload.ContractPayload -> {
                val contract = Contract(
                    publicKey = "DELETEME",
                    shareableToken = newContractMessage.shareableToken,
                    state = ContractState.CREATED,
                    body = Base64.getDecoder().decode(newContractMessage.signedMessage),
                    authorSession = authorSession,
                    createdAt = timeSource.nowInUtc(),
                    updatedAt = timeSource.nowInUtc()
                )

                this.contractRepository.save(contract)

                return contract
            }
            else -> {
                throw IllegalArgumentException("Invalid signature")
            }
        }
    }

    fun getPendingOnlineContracts(lockSession: LockSession) : Contract? {
        // First see if we have total control contracts that were created
        val tcContracts = this.contractRepository.findByShareableTokenAndStateOrderByCreatedAt(lockSession.totalControlToken!!, ContractState.CREATED)
        if(tcContracts.isNotEmpty()) {
            return tcContracts.first()
        }

        val normalContracts = this.contractRepository.findByShareableTokenAndStateOrderByCreatedAt(lockSession.totalControlToken!!, ContractState.ACCEPTED)
        if(normalContracts.isNotEmpty()) {
            return normalContracts.first()
        }

        return null
    }
}