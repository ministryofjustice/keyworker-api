package uk.gov.justice.digital.hmpps.gradle

import groovy.json.JsonException
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
import org.gradle.configurationcache.extensions.serviceOf
import java.io.IOException
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

abstract class CloudPlatformTask : DefaultTask() {
  init {
    group = "cloud-platform"
  }

  @Optional
  open var environment: Environment? = null
    @Input
    get
    @Option(option = "environment", description = "Environment hosted in Cloud Platform")
    set

  @get:OptionValues("environment")
  val environments = Environment.entries

  open var namespacePrefix: String? = null
    @Input
    get

  @get:Internal
  val userInput: UserInputHandler by lazy {
    project.serviceOf<UserInputHandler>()
  }


  @get:Internal
  val namespace: String by lazy {
    val namespacePrefix = namespacePrefix ?: throw IllegalArgumentException("Set task property `namespacePrefix`")
    val environment = environment
      ?: userInput.askUser { userQuestions ->
        userQuestions.selectOption("Choose environment", environments, Environment.dev)
      }
        .get()
    "$namespacePrefix-$environment"
  }

  protected fun generateRandomName(prefix: String, length: Int = 5): String {
    val bytes = ByteArray(length)
    SecureRandom().nextBytes(bytes)
    val suffix = buildString {
      for (byte in bytes) {
        append(String.format("%02x", byte))
      }
    }
    return "$prefix-$suffix"
  }

  @Internal
  protected fun getSecret(secret: String): Map<String, String>? {
    val process = ProcessBuilder(
      "kubectl", "--namespace", namespace,
      "get", "secret", secret,
      "--output", "json",
    ).start()
    val output = try {
      JsonSlurper().parse(process.inputStream) as Map<*, *>
    } catch (e: JsonException) {
      logger.warn("Secret `$secret` not found or unreadable")
      return null
    }
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw IOException("kubectl exited with code $exitCode")
    }
    return decodeSecretMap(output["data"] as Map<*, *>)
  }

  @Internal
  protected fun getAllSecrets(): Map<String, Map<String, String>> {
    val process = ProcessBuilder(
      "kubectl", "--namespace", namespace,
      "get", "secret",
      "--output", "json",
    ).start()
    val output = JsonSlurper().parse(process.inputStream) as Map<*, *>
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw IOException("kubectl exited with code $exitCode")
    }
    val items = output["items"] as List<*>
    return items.associate { it ->
      val item = it as Map<*, *>
      val metadata = item["metadata"] as Map<*, *>
      val data = decodeSecretMap(item["data"] as Map<*, *>)
      metadata["name"] as String to data
    }
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun decodeSecretMap(data: Map<*, *>): Map<String, String> {
    val decodedData = data.mapValues { (_, value) ->
      String(Base64.decode(value as String))
    }
    @Suppress("UNCHECKED_CAST")
    return decodedData as Map<String, String>
  }
}
