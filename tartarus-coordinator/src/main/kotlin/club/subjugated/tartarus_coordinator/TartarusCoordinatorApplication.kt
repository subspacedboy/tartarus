package club.subjugated.tartarus_coordinator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["club.subjugated"])
class TartarusCoordinatorApplication

fun main(args: Array<String>) {
	runApplication<TartarusCoordinatorApplication>(*args)
}
