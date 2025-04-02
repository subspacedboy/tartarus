package club.subjugated.tartarus_coordinator.api.messages

data class ConfigurationMessage(
    var apiUri: String?,
    var wsUri: String?,
    var webUri: String?,
    var mqttUri: String?,
    var safetyKeys: List<SafetyKeyMessage>?,
)
