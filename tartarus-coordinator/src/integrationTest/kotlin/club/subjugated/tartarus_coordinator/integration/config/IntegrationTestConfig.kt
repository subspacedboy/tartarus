package club.subjugated.tartarus_coordinator.integration.config

import club.subjugated.tartarus_coordinator.integration.helpers.FakeTimeSource
import club.subjugated.tartarus_coordinator.util.TimeSource
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Primary
import java.time.OffsetDateTime
import java.util.*

@TestConfiguration
@ComponentScan("club.subjugated.tartarus_coordinator.integration")
class IntegrationTestConfig {
    @Bean
    @Primary // This bean will be primary for the context of your tests
    fun myTimeSource(): TimeSource {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Return a mock or test implementation of MyBean for testing purposes
        return FakeTimeSource(OffsetDateTime.now())
    }
}