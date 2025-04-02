package club.subjugated.tartarus_coordinator.api.messages

import club.subjugated.tartarus_coordinator.models.SafetyKey
import club.subjugated.tartarus_coordinator.util.encodePublicKey
import java.util.*

data class SafetyKeyMessage(
    var name : String?,
    var publicKey : String?
){
    companion object {
        fun fromSafetyKey(safetyKey: SafetyKey) : SafetyKeyMessage {
            return SafetyKeyMessage(
                name = safetyKey.name,
                publicKey = Base64.getEncoder().encodeToString(safetyKey.publicKey)
            )
        }
    }
}