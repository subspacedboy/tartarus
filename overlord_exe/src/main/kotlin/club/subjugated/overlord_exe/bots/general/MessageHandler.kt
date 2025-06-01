package club.subjugated.overlord_exe.bots.general

import club.subjugated.overlord_exe.models.Contract

interface MessageHandler {
    fun reviewContracts(contracts : List<Contract>)
    fun handleAccept(contract: Contract)
    fun handleRelease(contract : Contract)
    fun handleLock(contract: Contract)
    fun handleUnlock(contract: Contract)
}