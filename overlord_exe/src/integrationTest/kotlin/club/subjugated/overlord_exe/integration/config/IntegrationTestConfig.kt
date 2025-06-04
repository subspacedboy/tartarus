package club.subjugated.overlord_exe.integration.config

import club.subjugated.overlord_exe.integration.helpers.FakeBlueskyService
import club.subjugated.overlord_exe.integration.helpers.FakeTimeSource
import club.subjugated.overlord_exe.services.BlueSkyService
import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Primary
import java.time.OffsetDateTime
import java.util.TimeZone

@TestConfiguration
//@ComponentScan("club.subjugated.overlord_exe")
class IntegrationTestConfig {
    @Bean
    @Primary // This bean will be primary for the context of your tests
    fun myTimeSource(): TimeSource {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Return a mock or test implementation of MyBean for testing purposes
        return FakeTimeSource(OffsetDateTime.now())
    }

    @Bean
    @Primary
    fun blueSkyService(): BlueSkyService = FakeBlueskyService()
}