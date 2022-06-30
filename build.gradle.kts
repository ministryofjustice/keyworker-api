plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.3.0"
  kotlin("plugin.spring") version "1.7.0"
  kotlin("plugin.jpa") version "1.7.0"
}
configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  annotationProcessor("org.projectlombok:lombok:1.18.22")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.22")
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
  implementation("org.apache.camel.springboot:camel-spring-boot:3.8.0") // DO NOT UPDATE - BREAKS CAMEL
  implementation("org.apache.camel:camel-quartz:3.8.0") // DO NOT UPDATE - BREAKS CAMEL
  implementation("org.apache.camel:camel-direct:3.8.0") // DO NOT UPDATE - BREAKS CAMEL
  implementation("org.springframework:spring-jms")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.0.6")
  implementation("javax.annotation:javax.annotation-api:1.3.2")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.sun.xml.bind:jaxb-impl:3.0.2")
  implementation("com.sun.xml.bind:jaxb-core:3.0.2")
  implementation("javax.activation:activation:1.1.1")
  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("io.swagger:swagger-annotations:1.6.6")
  implementation("org.springdoc:springdoc-openapi-ui:1.6.8")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.8")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.8")
  implementation("net.sf.ehcache:ehcache:2.10.9.2")
  implementation("org.apache.commons:commons-text:1.9")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.1")
  implementation("com.google.code.gson:gson:2.9.0")
  compileOnly("org.projectlombok:lombok:1.18.22")
  runtimeOnly("org.hsqldb:hsqldb:2.6.1")
  implementation("org.flywaydb:flyway-core:8.5.1")
  runtimeOnly("org.postgresql:postgresql:42.3.3")
  testImplementation("org.codehaus.groovy:groovy-all:3.0.9")
  testImplementation("org.spockframework:spock-spring:2.0-groovy-3.0") // Upgrade breaks groovy
  testImplementation("org.spockframework:spock-core:2.0-groovy-3.0") { // Upgrade breaks groovy
    exclude("org.codehaus.groovy")
  }
  testCompileOnly("org.projectlombok:lombok:1.18.22")
  testImplementation("org.gebish:geb-core:5.1")
  testImplementation("org.gebish:geb-spock:5.1")
  testImplementation("org.seleniumhq.selenium:selenium-support:4.1.2")
  testImplementation("org.seleniumhq.selenium:selenium-chrome-driver:4.1.2")
  testImplementation("org.seleniumhq.selenium:selenium-firefox-driver:4.1.2")
  testImplementation("io.github.http-builder-ng:http-builder-ng-apache:1.0.4")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("com.github.tomjankes:wiremock-groovy:0.2.0")
  testImplementation("org.apache.camel:camel-test-spring:3.8.0") // DO NOT UPDATE - BREAKS CAMEL
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.32.0")
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
