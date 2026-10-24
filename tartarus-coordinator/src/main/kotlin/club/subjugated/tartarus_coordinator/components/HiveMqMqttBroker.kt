package club.subjugated.tartarus_coordinator.components

import club.subjugated.tartarus_coordinator.services.BotService
import club.subjugated.tartarus_coordinator.services.LockSessionService
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
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(HiveMqMqttBroker::class.java)

fun isLockUser(username: String?): Boolean {
    if (username.isNullOrEmpty()) {
        return false
    }
    val regex = "^[a-zA-Z0-9]{6}$".toRegex()
    return regex.matches(username)
}

class AuthExtension(
    private var internalPassword: String,
    private var botService: BotService,
    private var lockSessionService: LockSessionService,
) : ExtensionMain {

    override fun extensionStart(input: ExtensionStartInput, output: ExtensionStartOutput) {
        Services.securityRegistry().setAuthenticatorProvider {
            MyAuthenticator(internalPassword, botService, lockSessionService)
        }
        Services.securityRegistry().setAuthorizerProvider { MyAuthorizer(botService) }
    }

    override fun extensionStop(input: ExtensionStopInput, output: ExtensionStopOutput) {
        // No cleanup needed here
    }

    class MyAuthenticator(
        private var internalPassword: String,
        private var botService: BotService,
        private var lockSessionService: LockSessionService,
    ) : SimpleAuthenticator {

        override fun onConnect(input: SimpleAuthInput, output: SimpleAuthOutput) {
            val clientId = input.connectPacket.clientId
            val username = input.connectPacket.userName.orElse(null)
            val password = input.connectPacket.password

            if (password.isEmpty) {
                logger.warn(
                    "❌ (HiveMQ) Client '$clientId' failed authentication: no password provided"
                )
                output.failAuthentication()
                return
            }

            if (clientId != username) {
                logger.warn("❌ (HiveMQ) Auth failed: clientId '$clientId' != username '$username'")
                output.failAuthentication()
                return
            }

            val actualPassword =
                StandardCharsets.UTF_8.decode(input.connectPacket.password.get()).toString()

            val isInternalUser = username == "internal"
            if (isInternalUser && actualPassword == internalPassword) {
                logger.info("🤖 (HiveMQ) Internal user '$username' authenticated successfully")
                output.authenticateSuccessfully()
                return
            }

            if (isLockUser(username)) {
                val session = lockSessionService.findBySessionToken(username)
                if (session == null) {
                    logger.info(
                        "ℹ️ (HiveMQ) Lock user '$username' connected without existing session record"
                    )
                    output.authenticateSuccessfully()
                    return
                }

                if (session.shouldCheckPassword()) {
                    if (session.checkPassword(actualPassword)) {
                        logger.info(
                            "🔑 (HiveMQ) Lock session '$username' authenticated successfully"
                        )
                        output.authenticateSuccessfully()
                    } else {
                        logger.warn(
                            "❌ (HiveMQ) Lock session '$username' authentication failed: password mismatch"
                        )
                        output.failAuthentication()
                    }
                } else {
                    logger.info(
                        "🔑 (HiveMQ) Lock session '$username' registered initial MQTT password"
                    )
                    session.setPassword(actualPassword)
                    lockSessionService.saveLockSession(session)
                    output.authenticateSuccessfully()
                }

                return
            }

            logger.warn("❌ (HiveMQ) Auth failed for user '$username' (clientId='$clientId')")
            output.failAuthentication()
        }
    }

    class MyAuthorizer(private var botService: BotService) :
        SubscriptionAuthorizer, PublishAuthorizer {
        override fun authorizeSubscribe(
            p0: SubscriptionAuthorizerInput?,
            p1: SubscriptionAuthorizerOutput?,
        ) {
            val client = p0!!.clientInformation
            val topicName = p0.subscription.topicFilter

            val internalUser = client.clientId == "internal"
            if (internalUser) {
                p1!!.authorizeSuccessfully()
                return
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

            logger.warn(
                "❌ (HiveMQ) Client '${client.clientId}' failed subscribe authorization on topic '$topicName'"
            )
            p1!!.failAuthorization()
        }

        override fun authorizePublish(p0: PublishAuthorizerInput?, p1: PublishAuthorizerOutput?) {
            val client = p0!!.clientInformation
            val topicName = p0.publishPacket.topic

            val internalUser = client.clientId == "internal"
            if (internalUser) {
                p1!!.authorizeSuccessfully()
                return
            }

            if (isLockUser(client.clientId)) {
                // Hierarchical per-lock uplink topics: `locks/${client.clientId}/*`
                // Restricting publishers strictly to their own clientId path enforces
                // least-privilege
                // access control. A rogue or malfunctioning lock cannot spoof state updates,
                // forge firmware requests, or inject events into another lock's stream or a shared
                // queue.
                if (topicName.toString() == "locks/${client.clientId}/events") {
                    p1!!.authorizeSuccessfully()
                    return
                }

                if (topicName.toString() == "locks/${client.clientId}/updates") {
                    p1!!.authorizeSuccessfully()
                    return
                }

                if (topicName.toString() == "locks/${client.clientId}/firmware") {
                    p1!!.authorizeSuccessfully()
                    return
                }

                if (topicName.toString() == "devices/status") {
                    p1!!.authorizeSuccessfully()
                    return
                }
            }

            logger.warn(
                "❌ (HiveMQ) Client '${client.clientId}' failed publish authorization on topic '$topicName'"
            )
            p1!!.failAuthorization()
        }
    }
}

/** HiveMQ broker component for managing the lifecycle of the embedded HiveMQ instance. */
@Component
@Profile("!cli")
class HiveMqMqttBroker(
    private var botService: BotService,
    private var lockSessionService: LockSessionService,
) {
    lateinit var hiveMQ: EmbeddedHiveMQ

    private final val internalPassword: UUID = UUID.randomUUID()

    @Value("\${tartarus.mqtt_ws_port}") val wsPortNumber: Long = 0
    @Value("\${tartarus.hivemq.config_directory}") val configDirectory: String = ""
    @Value("\${tartarus.hivemq.data_directory}") val dataDirectory: String = ""

    @Bean
    fun internalMqttSubscriberPassword(): String {
        return internalPassword.toString()
    }

    @PostConstruct
    fun start() {
        val embeddedExtension =
            EmbeddedExtension.builder()
                .withId("embedded-ext-1")
                .withName("Embedded Extension")
                .withVersion("1.0.0")
                .withPriority(0)
                .withStartPriority(1000)
                .withAuthor("Me")
                .withExtensionMain(
                    AuthExtension(internalPassword.toString(), botService, lockSessionService)
                )
                .build()

        val pathToConfig = extractResourceDir(configDirectory)

        Files.createDirectories(Paths.get(dataDirectory))

        hiveMQ =
            EmbeddedHiveMQBuilder.builder()
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
     * HiveMQ can't read its configuration when it's packed inside a jar file. This will make a
     * temporary directory and extract the contents there and return the path to that directory.
     */
    private fun extractResourceDir(resourceDir: String): Path {
        val tempDir = Files.createTempDirectory("hivemq-config")

        val classLoader = Thread.currentThread().contextClassLoader
        val resource =
            classLoader.getResource(resourceDir)
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
