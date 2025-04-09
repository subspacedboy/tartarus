package club.subjugated.overlord_exe.config

import club.subjugated.overlord_exe.util.TimeSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TimeSourceConfig {
    @Bean
    fun getTimeSource(): TimeSource {
        return TimeSource()
    }
}