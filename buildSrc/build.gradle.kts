import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "uk.gov.justice.digital.hmpps.gradle"
version = "1.0-SNAPSHOT"
description = "Custom gradle tasks"

plugins {
  kotlin("jvm") version "1.9.22"
}

repositories {
  mavenCentral()
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_21.toString()
  }
}
