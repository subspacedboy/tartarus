package club.subjugated.overlord_exe.bots.simple_proxy

enum class SimpleProxyState {
    // Placeholder default
    UNSPECIFIED,
    // The record has been created, but contract not issued
    CREATED,
    // Contract has been issued. Waiting for accept.
    ISSUED,
    // Contract was accepted and acknowledged in hardware
    ACCEPTED,
    // Contact has terminated and acknowledged in hardware
    RELEASED
}