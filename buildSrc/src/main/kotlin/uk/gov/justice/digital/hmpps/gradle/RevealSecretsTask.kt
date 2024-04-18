package uk.gov.justice.digital.hmpps.gradle

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * List kubernetes secrets or reveal their values
 *
 * ```build.gradle.kts
 * tasks.register<RevealSecretsTask>("revealSecrets") {
 *     namespacePrefix = "hmpps-????"
 * }
 * ```
 *
 * ```shell
 * ./gradlew help --task revealSecrets
 * ./gradlew revealSecrets
 * ./gradlew revealSecrets --environment dev --secret dps-rds-instance-output
 * ```
 */
@DisableCachingByDefault
open class RevealSecretsTask : CloudPlatformTask() {
  init {
    description = "Connect a local port to an AWS resource in Cloud Platform"
  }

  @Optional
  open var secretName: String? = null
    @Input
    get
    @Option(option = "secret", description = "Secret to reveal (lists all secrets if not provided)")
    set

  @TaskAction
  fun taskAction() {
    secretName?.let {
      revealSecret(it)
      return
    }
    listSecrets()
  }

  private fun revealSecret(secretName: String) {
    getSecret(secretName)?.let {
      if (it.isEmpty()) {
        println("Secret `$secretName` contains nothing")
        return
      }

      println("===============================================================================")
      it.forEach { (key, value) ->
        println("$key=$value")
      }
      println("===============================================================================")
      return
    }

    println("Secret `$secretName` not found")
  }

  private fun listSecrets() {
    val secrets = getAllSecrets()
    if (secrets.isEmpty()) {
      println("No secrets found")
      return
    }

    val maxNameLength = secrets.keys.maxOfOrNull { it.length }!!
    println("===============================================================================")
    secrets.forEach { (name, secret) ->
      val justifiedName = name.padEnd(maxNameLength)
      println("$justifiedName  ${secret.size}")
    }
    println("===============================================================================")
  }
}
