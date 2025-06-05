package club.subjugated.overlord_exe.models

import club.subjugated.overlord_exe.statemachines.Context
import club.subjugated.overlord_exe.statemachines.ContextProvider
import club.subjugated.overlord_exe.statemachines.InfoResolveMethod
import club.subjugated.overlord_exe.util.generateId
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import java.time.OffsetDateTime

@Entity
class StateMachine (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    var name: String = generateId("sm-"),
    @Enumerated(EnumType.STRING) var state: StateMachineState = StateMachineState.UNSPECIFIED,
    var ownedBy: String,
    var machineType: String,
    var machineVersion: String,
    @Column("machine_state")
    var currentState: String? = "",

    @Transient var context : Context? = null,
    @Transient var providerClass: ContextProvider<*, *>?,
//    @Transient var resolver: InfoResolver?,

    @OneToMany(mappedBy = "stateMachine", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var infoRequests: MutableList<InfoRequest> = mutableListOf(),
    @Enumerated(EnumType.STRING) var infoResolveMethod : InfoResolveMethod = InfoResolveMethod.USER,

    @ManyToOne @JoinColumn(name = "bsky_user_id") var bskyUser: BSkyUser? = null,

    @JsonFormat(shape = JsonFormat.Shape.STRING) var createdAt: OffsetDateTime? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING) var updatedAt: OffsetDateTime? = null,
)