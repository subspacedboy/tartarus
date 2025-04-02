package club.subjugated.tartarus_coordinator.models

interface PublicKeyProvider {
    fun decodePublicKey() : ByteArray
}