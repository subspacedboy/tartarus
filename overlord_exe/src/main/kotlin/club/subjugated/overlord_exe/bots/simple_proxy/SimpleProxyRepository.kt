package club.subjugated.overlord_exe.bots.simple_proxy

import club.subjugated.overlord_exe.models.BSkyUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SimpleProxyRepository : JpaRepository<SimpleProxy, Long> {
    fun findByName(name : String) : SimpleProxy
    fun findByContractSerialNumber(serialNumber : Long) : SimpleProxy
    fun findByBskyUserAndKeyHolderBskyUser(
        bskyUser: BSkyUser,
        keyHolderBskyUser: BSkyUser
    ): List<SimpleProxy>
    fun findByBskyUserAndKeyHolderBskyUserAndStateIn(
        bskyUser: BSkyUser,
        keyHolderBskyUser: BSkyUser,
        state : List<SimpleProxyState>
    ): List<SimpleProxy>
}