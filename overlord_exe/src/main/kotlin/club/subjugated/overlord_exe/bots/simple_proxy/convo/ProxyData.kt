package club.subjugated.overlord_exe.bots.simple_proxy.convo

import kotlinx.serialization.Serializable

@Serializable
data class ProxyData(
    var otherUserHandle: String
)
