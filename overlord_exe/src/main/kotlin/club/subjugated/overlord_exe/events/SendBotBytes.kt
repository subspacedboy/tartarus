package club.subjugated.overlord_exe.events

import club.subjugated.overlord_exe.models.BotMap
import org.eclipse.paho.client.mqttv3.MqttMessage

class SendBotBytes(
    val source: Any,
    val botMap: BotMap,
    val message: MqttMessage
) {
}