package uk.gov.justice.digital.hmpps.gradle

import org.gradle.api.tasks.Input

/**
 * Connect a local port to ElastiCache Redis in Cloud Platform
 *
 * ```build.gradle.kts
 * tasks.register<PortForwardRedisTask>("portForwardRedis") {
 *     namespacePrefix = "hmpps-????"
 * }
 * ```
 *
 * ```shell
 * ./gradlew help --task portForwardRedis
 * ./gradlew portForwardRedis --environment dev
 * ```
 */
open class PortForwardRedisTask : PortForwardTask() {
  init {
    description = "Connect a local port to ElastiCache Redis in Cloud Platform"
  }

  override var secretName: String? = "elasticache-redis"
    @Input
    get

  override var remotePort: Int? = 6379
    @Input
    get

  override fun getRemoteHostFromSecret(secret: Map<String, String>): String {
    return secret["primary_endpoint_address"]!!
  }

  override fun showUsageInstructions(secret: Map<String, String>) {
    println(
      """
      Usage example:
      `redis-cli -h 127.0.0.1 -p $localPort --tls --insecure --askpass`
      Get Redis auth token with:
      `kubectl --namespace $namespace get secret $secretName -o jsonpath={.data.auth_token} | base64 -D`
      NB: Redis client must support TLS but must not check certificates as the domain will be incorrect.
      Close port-forwarding connection with Ctrl-C. 
      """.trimIndent(),
    )
  }
}
