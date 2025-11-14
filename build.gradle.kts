import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "9.1.4"
  kotlin("plugin.spring") version "2.2.21"
  kotlin("plugin.jpa") version "2.2.21"
  id("io.gatling.gradle") version "3.14.9"
  jacoco
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework:spring-webflux")
  implementation("org.springframework.boot:spring-boot-starter-reactor-netty")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.6.1")
  implementation("io.swagger:swagger-annotations:1.6.16")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.14")
  implementation("io.opentelemetry:opentelemetry-api:1.56.0")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.21.0")
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.26.0")
  implementation("com.fasterxml.uuid:java-uuid-generator:5.1.1")
  implementation("org.hibernate.orm:hibernate-envers")
  implementation("org.springframework.data:spring-data-envers")
  implementation("org.openapitools:jackson-databind-nullable:0.2.8")
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.8.1")

  implementation("org.flywaydb:flyway-core")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  testImplementation("org.awaitility:awaitility:4.3.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
  testImplementation("io.jsonwebtoken:jjwt:0.13.0")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.testcontainers:postgresql:1.21.3")
  testImplementation("org.testcontainers:localstack:1.21.3")
}

dependencyCheck {
  suppressionFiles.addAll(listOf("suppressions.xml", ".dependency-check-ignore.xml"))
  nvd.datafeedUrl = "file:///opt/vulnz/cache"
}

kotlin {
  jvmToolchain(21)
}

tasks {

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
      jvmTarget = JVM_21
      freeCompilerArgs.addAll(
        "-Xwhen-guards",
        "-Xannotation-default-target=param-property",
      )
    }
  }
  test {
    if (project.hasProperty("init-db")) {
      include("**/InitialiseDatabase.class")
    } else {
      exclude("**/InitialiseDatabase.class")
    }
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

dependencyCheck {
  suppressionFile = ".dependency-check-ignore.xml"
}
