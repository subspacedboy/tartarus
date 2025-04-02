package club.subjugated.tartarus_coordinator.config

import club.subjugated.tartarus_coordinator.util.TimeSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TimeSourceConfig {
    @Bean
    fun getTimeSource(): TimeSource {
        return TimeSource()
    }
}
