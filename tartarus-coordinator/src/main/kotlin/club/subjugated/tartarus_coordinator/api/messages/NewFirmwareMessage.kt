package club.subjugated.tartarus_coordinator.api.messages

data class NewFirmwareMessage(
    var firmware : ByteArray
) {
}