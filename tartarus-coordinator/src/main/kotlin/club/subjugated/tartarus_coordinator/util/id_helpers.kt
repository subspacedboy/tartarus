package club.subjugated.tartarus_coordinator.util

import org.apache.commons.codec.binary.Base32
import java.time.format.DateTimeFormatter
import kotlin.random.Random

val encoder = Base32(true)

val ulidFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

fun generateId(prefix: String = ""): String {
    val someBytes = Random.nextBytes(4)
    return prefix + encoder.encodeToString(someBytes).replace("=","")
}

fun generateUlid(prefix: String = "", timeSource: TimeSource = TimeSource()): String {
    val now = timeSource.nowInUtc()
    val timestamp = now.format(ulidFormatter)
    val someBytes = Random.nextBytes(4)
    return prefix + timestamp + encoder.encodeToString(someBytes).replace("=","")
}