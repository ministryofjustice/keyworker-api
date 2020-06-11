plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "0.4.1"
}

configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

extra["spring-security.version"] = "5.3.2.RELEASE" // Updated since spring-boot-starter-oauth2-resource-server-2.2.5.RELEASE only pulls in 5.2.2.RELEASE (still affected by CVE-2018-1258 though)


dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok:1.18.12")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.12")

  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-quartz")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("org.springframework.security:spring-security-oauth2-jose")
  implementation("org.springframework:spring-webflux")
  implementation("org.springframework.boot:spring-boot-starter-reactor-netty")

  implementation("org.apache.camel:camel-spring-boot:2.25.1")
  implementation("org.apache.camel:camel-quartz2:2.25.1")

  implementation("org.springframework:spring-jms")
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")

  implementation("javax.annotation:javax.annotation-api:1.3.2")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.sun.xml.bind:jaxb-impl:2.3.3")
  implementation("com.sun.xml.bind:jaxb-core:2.3.0.1")
  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")

  implementation("io.springfox:springfox-swagger2:2.9.2")
  implementation("io.springfox:springfox-swagger-ui:2.9.2")

  implementation("net.sf.ehcache:ehcache:2.10.6")
  implementation("org.apache.commons:commons-text:1.8")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.11.0")

  implementation("com.google.code.gson:gson:2.8.6")

  compileOnly("org.projectlombok:lombok:1.18.12")

  runtimeOnly("org.hsqldb:hsqldb:2.5.0")
  runtimeOnly("org.flywaydb:flyway-core:6.4.4")
  runtimeOnly("org.postgresql:postgresql:42.2.14")

  testImplementation("org.codehaus.groovy:groovy-all:3.0.4")
  testImplementation("org.spockframework:spock-spring:2.0-M2-groovy-3.0")
  testCompile("org.spockframework:spock-core:2.0-M2-groovy-3.0") {
    exclude("org.codehaus.groovy")
  }

  testCompileOnly("org.projectlombok:lombok:1.18.12")
  testImplementation("org.gebish:geb-core:3.4")
  testImplementation("org.gebish:geb-spock:3.4")
  testImplementation("org.seleniumhq.selenium:selenium-support:3.141.59")
  testImplementation("org.seleniumhq.selenium:selenium-chrome-driver:3.141.59")
  testImplementation("org.seleniumhq.selenium:selenium-firefox-driver:3.141.59")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.26.3")
  testImplementation("com.github.tomjankes:wiremock-groovy:0.2.0")
  testImplementation("org.apache.camel:camel-test-spring:2.25.0")
  testImplementation("com.nhaarman:mockito-kotlin-kt1.1:1.6.0")
  testImplementation("org.testcontainers:localstack:1.14.3")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.17.0")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
}
