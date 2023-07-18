plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.2.4"
  kotlin("plugin.spring") version "1.9.0"
  kotlin("plugin.jpa") version "1.9.0"
}
configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok")
  testAnnotationProcessor("org.projectlombok:lombok")
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
  implementation("org.apache.camel.springboot:camel-spring-boot:3.20.4") // DO NOT UPDATE - BREAKS CAMEL
  implementation("org.apache.camel:camel-quartz:3.20.4") // DO NOT UPDATE - BREAKS CAMEL
  implementation("org.apache.camel:camel-direct:3.20.4") // DO NOT UPDATE - BREAKS CAMEL
  implementation("org.apache.camel:camel-bean:3.20.4") // DO NOT UPDATE - BREAKS CAMEL
  implementation("org.springframework:spring-jms")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.3.1")
  implementation("javax.annotation:javax.annotation-api:1.3.2")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.sun.xml.bind:jaxb-impl:4.0.3")
  implementation("com.sun.xml.bind:jaxb-core:4.0.3")
  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("io.swagger:swagger-annotations:1.6.11")
  implementation("org.springdoc:springdoc-openapi-ui:1.7.0")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.7.0")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.7.0")
  implementation("net.sf.ehcache:ehcache:2.10.9.2")
  implementation("org.apache.commons:commons-text:1.10.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
  implementation("com.google.code.gson:gson")
  implementation("com.google.guava:guava:32.1.1-jre")
  compileOnly("org.projectlombok:lombok")
  runtimeOnly("org.hsqldb:hsqldb:2.7.2")
  implementation("org.flywaydb:flyway-core")
  runtimeOnly("org.postgresql:postgresql")
  testImplementation("org.codehaus.groovy:groovy-all:3.0.18")
  testImplementation("org.spockframework:spock-spring:2.0-groovy-3.0") // Upgrade breaks groovy
  testImplementation("org.spockframework:spock-core:2.0-groovy-3.0") { // Upgrade breaks groovy
    exclude("org.codehaus.groovy")
  }
  testCompileOnly("org.projectlombok:lombok")
  testImplementation("org.gebish:geb-core:6.0") // Upgrade breaks groovy
  testImplementation("org.gebish:geb-spock:6.0") // Upgrade breaks groovy
  testImplementation("org.seleniumhq.selenium:selenium-support:4.10.0")
  testImplementation("org.seleniumhq.selenium:selenium-chrome-driver:4.10.0")
  testImplementation("org.seleniumhq.selenium:selenium-firefox-driver:4.10.0")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("com.github.tomjankes:wiremock-groovy:0.2.0")
  testImplementation("org.apache.camel:camel-test-spring:3.20.4") // DO NOT UPDATE - BREAKS CAMEL
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.38.0")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.springframework.security:spring-security-test")
}
dependencyCheck {
  suppressionFiles.add("suppressions.xml")
}

tasks {
  compileKotlin {
    kotlinOptions {
      jvmTarget = "18"
    }
  }

  test {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
  }
}
