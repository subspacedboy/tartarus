package club.subjugated.overlord_exe.models

import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.OffsetDateTime

@Entity
class Contract(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("bc-"),
    var externalContractName: String? = null,
    var botName : String,
    var lockSessionToken: String?,
    var shareableToken: String? = null,
    var serialNumber: Int,
    @Enumerated(EnumType.STRING) var state: ContractState = ContractState.UNSPECIFIED,

    // The original contract bytes are always returned from GetContractResponse but we don't
    // don't need to save them ourselves.
    @Transient var signedMessage : ByteArray? = null,

    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
) {
}