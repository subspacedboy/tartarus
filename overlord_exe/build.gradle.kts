import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

plugins {
	kotlin("jvm") version "2.1.0"
	kotlin("plugin.spring") version "2.1.0"

	id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"

	id("org.springframework.boot") version "3.4.4"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.1.0"

	id("idea")
}

group = "club.subjugated"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.repsy.io/mvn/uakihir0/public") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-jersey")
	implementation("org.springframework.boot:spring-boot-starter-quartz")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.mysql:mysql-connector-j")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
	implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
	implementation("org.bouncycastle:bcprov-jdk18on:1.80")
	implementation("info.picocli:picocli:4.7.6")

	implementation("work.socialhub.kbsky:core-jvm:0.4.0-SNAPSHOT") {
		exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
	}
	implementation("work.socialhub.kbsky:auth-jvm:0.4.0-SNAPSHOT") {
		exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
	}
	implementation("work.socialhub.kbsky:stream-jvm:0.4.0-SNAPSHOT") {
		exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
	}

	// https://mvnrepository.com/artifact/commons-codec/commons-codec
	implementation("commons-codec:commons-codec:1.18.0")
	var ktorVersion = "3.1.2"
	implementation("io.ktor:ktor-client-core:$ktorVersion")
	implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
	implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
	implementation("io.ktor:ktor-client-okhttp:$ktorVersion")

	implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.8.1")
}

// ktor, or something, is somehow importing 1.6.3 of kotlinx-serialization despite
// trying to override and block it. So we're just going to rewrite the dependency.
configurations.all {
	resolutionStrategy {
		force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
		force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
		force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")

		eachDependency {
			if (requested.group == "org.jetbrains.kotlinx" &&
				requested.name.contains("serialization")) {
				useVersion("1.8.1")
			}
		}
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}

	sourceSets.all {
		languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	val today = Instant.now().atZone(ZoneId.of("UTC"))
	val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
	this.archiveFileName.set(
		"${archiveBaseName.get()}-${formatter.format(today)}.${archiveExtension.get()}"
	)
}