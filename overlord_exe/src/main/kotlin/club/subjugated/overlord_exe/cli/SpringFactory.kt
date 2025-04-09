package club.subjugated.overlord_exe.cli

import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import picocli.CommandLine

@Component
class SpringFactory(
    private val context: ApplicationContext
) : CommandLine.IFactory {
    override fun <K : Any?> create(cls: Class<K>): K = context.getBean(cls)
}