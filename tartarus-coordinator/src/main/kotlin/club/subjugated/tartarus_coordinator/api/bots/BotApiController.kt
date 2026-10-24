package club.subjugated.tartarus_coordinator.api.bots

import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.Table

class ResponseBuilderContext<T : Table>(val builder: FlatBufferBuilder, val messageOffset: Int)

/**
 * Retired: The MQTT-based FlatBuffers Bot API (`BotApiMessage` on `coordinator/inbox`) has been
 * replaced by AuthorSession-owned bots communicating via REST (`/bots/...`) and real-time
 * WebSockets (`/ws/events`).
 */
class BotApiController
