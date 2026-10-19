package club.subjugated.tartarus_coordinator.models

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventTest {

    @Test
    fun eventDefaultNameStartsWithEvPrefix() {
        val event1 = Event()
        val event2 = Event()
        assertThat(event1.name).startsWith("ev-")
        assertThat(event2.name).startsWith("ev-")
        assertThat(event1.name).isNotEqualTo(event2.name)
    }
}
