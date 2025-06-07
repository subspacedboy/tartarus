package club.subjugated.overlord_exe.config

import club.subjugated.overlord_exe.components.BSkyDmMonitor
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")
class QuartzConfig {
    @Bean
    fun jobDetail(): JobDetail =
        JobBuilder.newJob(BSkyDmMonitor::class.java)
            .withIdentity("BSky DM Monitor")
            .storeDurably()
            .build()

    @Bean
    fun trigger(jobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(jobDetail)
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInSeconds(4)
                    .repeatForever()
            )
            .build()
}