import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
import uk.gov.justice.digital.hmpps.gradle.PortForwardRDSTask
import uk.gov.justice.digital.hmpps.gradle.PortForwardRedisTask
import uk.gov.justice.digital.hmpps.gradle.RevealSecretsTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "8.1.0"
  kotlin("plugin.spring") version "2.1.21"
  kotlin("plugin.jpa") version "2.1.21"
  jacoco
}

// DO NOT UPDATE - BREAKS CAMEL / GROOVY
val spockVersion = "2.4-M1-groovy-3.0"
val gebishVersion = "6.0"

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok")
  testAnnotationProcessor("org.projectlombok:lombok")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.security:spring-security-oauth2-jose")
  implementation("org.springframework:spring-webflux")
  implementation("org.springframework.boot:spring-boot-starter-reactor-netty")
  implementation("org.springframework.retry:spring-retry")
  implementation("org.springframework:spring-jms")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.4.4")
  implementation("javax.annotation:javax.annotation-api:1.3.2")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.sun.xml.bind:jaxb-impl")
  implementation("com.sun.xml.bind:jaxb-core")
  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("io.swagger:swagger-annotations:1.6.16")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
  implementation("org.apache.commons:commons-text:1.13.1")
  implementation("com.google.guava:guava:32.1.3-jre")
  implementation("io.opentelemetry:opentelemetry-api:1.50.0")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.16.0")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.12.0")
  implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")
  implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.10")
  implementation("org.hibernate.orm:hibernate-envers")
  implementation("org.springframework.data:spring-data-envers")

  compileOnly("org.projectlombok:lombok")
  runtimeOnly("com.h2database:h2:2.3.232")
  implementation("org.flywaydb:flyway-core")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")
  testImplementation("org.codehaus.groovy:groovy-all:3.0.24")
  testImplementation("org.spockframework:spock-spring:$spockVersion") // Upgrade breaks groovy
  testImplementation("org.spockframework:spock-core:$spockVersion") {
    // Upgrade breaks groovy
    exclude("org.codehaus.groovy")
  }
  testCompileOnly("org.projectlombok:lombok")

  testImplementation("org.awaitility:awaitility:4.3.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
  testImplementation("org.gebish:geb-core:$gebishVersion") // Upgrade breaks groovy
  testImplementation("org.gebish:geb-spock:$gebishVersion") // Upgrade breaks groovy
  testImplementation("org.seleniumhq.selenium:selenium-support:4.32.0")
  testImplementation("org.seleniumhq.selenium:selenium-chrome-driver:4.32.0")
  testImplementation("org.seleniumhq.selenium:selenium-firefox-driver:4.32.0")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomjankes:wiremock-groovy:0.2.0")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:4.1.1")
  testImplementation("io.jsonwebtoken:jjwt:0.12.6")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.testcontainers:postgresql:1.21.0")
  testImplementation("org.testcontainers:localstack:1.21.0")
}

allOpen {
  annotations(
    "jakarta.persistence.Entity",
    "jakarta.persistence.MappedSuperclass",
    "jakarta.persistence.Embeddable",
  )
}

dependencyCheck {
  suppressionFiles.add("suppressions.xml")
}

tasks {
  register<PortForwardRDSTask>("portForwardRDS") {
    namespacePrefix = "keyworker-api"
  }

  register<PortForwardRedisTask>("portForwardRedis") {
    namespacePrefix = "keyworker-api"
  }

  register<RevealSecretsTask>("revealSecrets") {
    namespacePrefix = "keyworker-api"
  }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
      jvmTarget = JVM_21
      freeCompilerArgs.add("-Xwhen-guards")
    }
  }
  test {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
  }

  register("initialiseDatabase", Test::class) {
    include("**/InitialiseDatabase.class")
  }

  test {
    exclude("**/InitialiseDatabase.class")
  }

  getByName("initialiseDatabase") {
    onlyIf { gradle.startParameter.taskNames.contains("initialiseDatabase") }
  }
}

tasks.withType<Jar> {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Jacoco code coverage
tasks.named("test") {
  finalizedBy("jacocoTestReport")
}

tasks.named<JacocoReport>("jacocoTestReport") {
  reports {
    html.required.set(true)
    xml.required.set(true)
  }
}
