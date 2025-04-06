package club.subjugated.tartarus_coordinator.components

import club.subjugated.tartarus_coordinator.services.BotService
import com.hivemq.embedded.EmbeddedExtension
import com.hivemq.embedded.EmbeddedHiveMQ
import com.hivemq.embedded.EmbeddedHiveMQBuilder
import com.hivemq.extension.sdk.api.ExtensionMain
import com.hivemq.extension.sdk.api.auth.PublishAuthorizer
import com.hivemq.extension.sdk.api.auth.SimpleAuthenticator
import com.hivemq.extension.sdk.api.auth.SubscriptionAuthorizer
import com.hivemq.extension.sdk.api.auth.parameter.PublishAuthorizerInput
import com.hivemq.extension.sdk.api.auth.parameter.PublishAuthorizerOutput
import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthInput
import com.hivemq.extension.sdk.api.auth.parameter.SimpleAuthOutput
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerInput
import com.hivemq.extension.sdk.api.auth.parameter.SubscriptionAuthorizerOutput
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput
import com.hivemq.extension.sdk.api.services.Services
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

fun isLockUser(username: String?): Boolean {
    if (username.isNullOrEmpty()) {
        return false
    }
    val regex = "^[a-zA-Z0-9]{6}$".toRegex()
    return regex.matches(username)
}

class AuthExtension(
    private var internalPassword : String,
    private var botService: BotService
) : ExtensionMain {

    override fun extensionStart(input: ExtensionStartInput, output: ExtensionStartOutput) {
        Services.securityRegistry().setAuthenticatorProvider {
            MyAuthenticator(internalPassword, botService)
        }
        Services.securityRegistry().setAuthorizerProvider {
            MyAuthorizer(botService)
        }
    }

    override fun extensionStop(input: ExtensionStopInput, output: ExtensionStopOutput) {
        // No cleanup needed here
    }

    class MyAuthenticator(
        private var internalPassword: String,
        private var botService: BotService) : SimpleAuthenticator {

        override fun onConnect(input: SimpleAuthInput, output: SimpleAuthOutput) {
            val clientId = input.connectPacket.clientId
            val username = input.connectPacket.userName.orElse(null)
            val password = input.connectPacket.password

            if(password.isEmpty) {
                output.failAuthentication()
                return
            }

            if(clientId != username) {
                output.failAuthentication()
                return
            }

            val actualPassword = StandardCharsets.UTF_8.decode(input.connectPacket.password.get()).toString()

            val isInternalUser = username == "internal" || username == "bot_processor"
            if(isInternalUser && actualPassword == internalPassword) {
                output.authenticateSuccessfully()
                return
            }

            if(username.startsWith("b-")) {
                val bot = botService.getByName(username)
                if(bot.checkPassword(actualPassword)) {
                    output.authenticateSuccessfully()
                    return
                }
            }

            if(isLockUser(username)) {
                output.authenticateSuccessfully()
                return
            }

            output.failAuthentication()
        }
    }

    class MyAuthorizer(
        private var botService: BotService
    ) : SubscriptionAuthorizer, PublishAuthorizer {
        override fun authorizeSubscribe(p0: SubscriptionAuthorizerInput?, p1: SubscriptionAuthorizerOutput?) {
            val client = p0!!.clientInformation
            val topicName = p0.subscription.topicFilter

            val internalUser = client.clientId == "bot_processor" || client.clientId == "internal"
            if(internalUser) {
                p1!!.authorizeSuccessfully()
                return
            }

            if(client.clientId.startsWith("b-")) {
                val bot = botService.getByName(client.clientId)
                if(bot.canReadMqtt(topicName)){
                    p1!!.authorizeSuccessfully()
                    return
                }
            }

            if (topicName.toString() == "locks/${client.clientId}") {
                p1!!.authorizeSuccessfully()
                return
            }

            if (topicName.toString() == "configuration/${client.clientId}") {
                p1!!.authorizeSuccessfully()
                return
            }

            if (topicName.toString() == "firmware/${client.clientId}") {
                p1!!.authorizeSuccessfully()
                return
            }
        }

        override fun authorizePublish(p0: PublishAuthorizerInput?, p1: PublishAuthorizerOutput?) {
            val client = p0!!.clientInformation
            val topicName = p0.publishPacket.topic

            val internalUser = client.clientId == "bot_processor" || client.clientId == "internal"
            if(internalUser) {
                p1!!.authorizeSuccessfully()
                return
            }

            if(client.clientId.startsWith("b-")) {
                val bot = botService.getByName(client.clientId)
                if(bot.canWriteMqtt(topicName)){
                    p1!!.authorizeSuccessfully()
                    return
                }
            }

            if(isLockUser(client.clientId)){
                if(topicName!!.startsWith("bots/inbox_events_b-")){
                    p1!!.authorizeSuccessfully()
                    return
                }

                if(topicName.toString() == "locks/updates") {
                    p1!!.authorizeSuccessfully()
                    return
                }

                if(topicName.toString() == "locks/firmware") {
                    p1!!.authorizeSuccessfully()
                    return
                }

                if(topicName.toString() == "devices/status") {
                    p1!!.authorizeSuccessfully()
                    return
                }
            }

            p1!!.failAuthorization()
        }
    }
}

/**
 * HiveMQ broker component for managing the lifecycle of the embedded HiveMQ instance.
 */
@Component
@Profile("!cli")
class HiveMqMqttBroker(
    private var botService: BotService
) {
    lateinit var hiveMQ: EmbeddedHiveMQ

    private final val internalPassword: UUID = UUID.randomUUID()

    @Value("\${tartarus.mqtt_ws_port}") val wsPortNumber: Long = 0
    @Value("\${tartarus.hivemq.config_directory}") val configDirectory: String = ""
    @Value("\${tartarus.hivemq.data_directory}") val dataDirectory: String = ""

    @Bean
    fun internalMqttSubscriberPassword() : String {
        return internalPassword.toString()
    }

    @PostConstruct
    fun start() {
        val embeddedExtension = EmbeddedExtension.builder()
            .withId("embedded-ext-1")
            .withName("Embedded Extension")
            .withVersion("1.0.0")
            .withPriority(0)
            .withStartPriority(1000)
            .withAuthor("Me")
            .withExtensionMain(AuthExtension(internalPassword.toString(), botService))
            .build()

        val pathToConfig = extractResourceDir(configDirectory)

        Files.createDirectories(Paths.get(dataDirectory))

        hiveMQ = EmbeddedHiveMQBuilder.builder()
            .withDataFolder(Path.of(dataDirectory)) // Specify the data folder for persistence
            .withoutLoggingBootstrap()
            .withEmbeddedExtension(embeddedExtension)
            .withConfigurationFolder(pathToConfig)
            .build()

        println("💬 (HiveMQ) Started")

        // If we don't wait for this there's no guarantee the listeners will be setup
        // by the time we need to connect.
        hiveMQ.start().join()
    }

    /**
     * HiveMQ can't read its configuration when it's packed inside a jar file. This will make
     * a temporary directory and extract the contents there and return the path to that directory.
     */
    private fun extractResourceDir(resourceDir: String): Path {
        val tempDir = Files.createTempDirectory("hivemq-config")

        val classLoader = Thread.currentThread().contextClassLoader
        val resource = classLoader.getResource(resourceDir)
            ?: error("Resource directory not found: $resourceDir")

        val uri = resource.toURI()

        if (uri.scheme == "jar") {
            // Walk inside the JAR and copy to temp
            val fs = FileSystems.newFileSystem(uri, mapOf<String, Any>())
            val jarPath = fs.getPath("/$resourceDir")

            Files.walk(jarPath).forEach { path ->
                val dest = tempDir.resolve(jarPath.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest)
                } else {
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } else {
            // Resource is on the real filesystem already
            val srcPath = Path.of(uri)
            Files.walk(srcPath).forEach { path ->
                val dest = tempDir.resolve(srcPath.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest)
                } else {
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        return tempDir
    }

    @PreDestroy
    fun stop() {
        // Same as start, this needs to be joined on to be safe.
        hiveMQ.stop().join()
        println("🛑 (HiveMQ) MQTT Broker stopped")
    }
}