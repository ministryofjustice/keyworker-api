plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.3.8"
  kotlin("plugin.spring") version "1.5.30"
  kotlin("plugin.jpa") version "1.5.30"
}
configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok:1.18.20")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.20")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-quartz")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.security:spring-security-oauth2-jose")
  implementation("org.springframework:spring-webflux")
  implementation("org.springframework.boot:spring-boot-starter-reactor-netty")
  implementation("org.springframework.retry:spring-retry")
  implementation("org.apache.camel.springboot:camel-spring-boot:3.8.0")
  implementation("org.apache.camel:camel-quartz:3.8.0")
  implementation("org.apache.camel:camel-direct:3.8.0")
  implementation("org.springframework:spring-jms")
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
  implementation("javax.annotation:javax.annotation-api:1.3.2")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.sun.xml.bind:jaxb-impl:3.0.2")
  implementation("com.sun.xml.bind:jaxb-core:3.0.2")
  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("io.springfox:springfox-boot-starter:3.0.0")
  implementation("net.sf.ehcache:ehcache:2.10.9.2")
  implementation("org.apache.commons:commons-text:1.9")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.5")
  implementation("com.google.code.gson:gson:2.8.8")
  compileOnly("org.projectlombok:lombok:1.18.20")
  runtimeOnly("org.hsqldb:hsqldb:2.6.0")
  implementation("org.flywaydb:flyway-core:7.14.1")
  runtimeOnly("org.postgresql:postgresql:42.2.23")
  testImplementation("org.codehaus.groovy:groovy-all:3.0.8")
  testImplementation("org.spockframework:spock-spring:2.0-groovy-3.0")
  testImplementation("org.spockframework:spock-core:2.0-groovy-3.0") {
    exclude("org.codehaus.groovy")
  }
  testCompileOnly("org.projectlombok:lombok:1.18.20")
  testImplementation("org.gebish:geb-core:5.0")
  testImplementation("org.gebish:geb-spock:5.0")
  testImplementation("org.seleniumhq.selenium:selenium-support:3.141.59")
  testImplementation("org.seleniumhq.selenium:selenium-chrome-driver:3.141.59")
  testImplementation("org.seleniumhq.selenium:selenium-firefox-driver:3.141.59")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("com.github.tomjankes:wiremock-groovy:0.2.0")
  testImplementation("org.apache.camel:camel-test-spring:3.8.0")
  testImplementation("com.nhaarman:mockito-kotlin-kt1.1:1.6.0")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.28.0")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.springframework.security:spring-security-test")
}
dependencyCheck {
  suppressionFiles.add("suppressions.xml")
}

tasks {
  compileKotlin {
    kotlinOptions {
      jvmTarget = "16"
    }
  }

  test {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
  }
}
