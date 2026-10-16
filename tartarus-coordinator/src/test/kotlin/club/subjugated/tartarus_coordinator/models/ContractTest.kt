package club.subjugated.tartarus_coordinator.models

import club.subjugated.fb.message.SignedMessage
import com.google.flatbuffers.FlatBufferBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContractTest {

    private fun createSignedContractBytes(isEmergencyAllowed: Boolean?): ByteArray {
        val builder = FlatBufferBuilder(1024)
        val termsOffset = builder.createString("Test terms")
        val serialNumber = 42

        club.subjugated.fb.message.Contract.startContract(builder)
        club.subjugated.fb.message.Contract.addSerialNumber(builder, serialNumber.toUShort())
        club.subjugated.fb.message.Contract.addTerms(builder, termsOffset)
        club.subjugated.fb.message.Contract.addIsTemporaryUnlockAllowed(builder, false)
        if (isEmergencyAllowed != null) {
            club.subjugated.fb.message.Contract.addIsEmergencyUnlockAllowed(builder, isEmergencyAllowed)
        }
        val contractOffset = club.subjugated.fb.message.Contract.endContract(builder)

        SignedMessage.startSignedMessage(builder)
        SignedMessage.addPayload(builder, contractOffset)
        SignedMessage.addPayloadType(builder, club.subjugated.fb.message.MessagePayload.Contract)
        val signedMessageOffset = SignedMessage.endSignedMessage(builder)
        builder.finish(signedMessageOffset)

        return builder.sizedByteArray()
    }

    private fun makeContract(body: ByteArray?): Contract {
        val author = AuthorSession(publicKey = "pk")
        val lock = LockSession(publicKey = "pk", sessionToken = "tok", shareToken = "sh", totalControlToken = "tc")
        return Contract(
            shareableToken = "tok",
            authorSession = author,
            lockSession = lock,
            body = body
        )
    }

    @Test
    fun testEmergencyUnlockDefaultsToTrueWhenFieldAbsent() {
        val contractBytes = createSignedContractBytes(isEmergencyAllowed = null)
        val contract = makeContract(contractBytes)
        assertThat(contract.isEmergencyUnlockAllowed()).isTrue()
    }

    @Test
    fun testEmergencyUnlockExplicitlyTrue() {
        val contractBytes = createSignedContractBytes(isEmergencyAllowed = true)
        val contract = makeContract(contractBytes)
        assertThat(contract.isEmergencyUnlockAllowed()).isTrue()
    }

    @Test
    fun testEmergencyUnlockExplicitlyFalse() {
        val contractBytes = createSignedContractBytes(isEmergencyAllowed = false)
        val contract = makeContract(contractBytes)
        assertThat(contract.isEmergencyUnlockAllowed()).isFalse()
    }

    @Test
    fun testEmergencyUnlockNullBodyDefaultsToTrue() {
        val contract = makeContract(null)
        assertThat(contract.isEmergencyUnlockAllowed()).isTrue()
    }

    @Test
    fun testContractMessageReflectsEmergencyUnlocked() {
        val contract = makeContract(null)
        contract.lockState = false
        contract.emergencyUnlocked = true

        val msg = club.subjugated.tartarus_coordinator.api.messages.ContractMessage.fromContract(contract)
        assertThat(msg.lockState).isNotNull
        assertThat(msg.lockState?.isLocked).isFalse()
        assertThat(msg.lockState?.emergencyUnlocked).isTrue()
    }
}
