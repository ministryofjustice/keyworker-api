package uk.gov.justice.digital.hmpps.keyworker.config.container

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.net.ServerSocket

object LocalStackContainer {
  val instance by lazy { startMiniStackIfNotRunning() }

  fun setMiniStackProperties(
    miniStackContainer: GenericContainer<*>,
    registry: DynamicPropertyRegistry,
  ) {
    val endpoint = "http://${miniStackContainer.host}:${miniStackContainer.getMappedPort(4566)}"
    val region = miniStackContainer.envMap.getOrDefault("DEFAULT_REGION", "eu-west-2")
    registry.add("hmpps.sqs.localstackUrl") { endpoint }
    registry.add("hmpps.sqs.region") { region }
  }

  private fun startMiniStackIfNotRunning(): GenericContainer<*>? {
    if (miniStackIsRunning()) return null
    return GenericContainer(DockerImageName.parse("nahuelnucera/ministack:latest")).apply {
      withExposedPorts(4566)
      withEnv("DEFAULT_REGION", "eu-west-2")
      waitingFor(Wait.forHttp("/_localstack/health").forPort(4566))
      start()
    }
  }

  private fun miniStackIsRunning(): Boolean =
    try {
      val serverSocket = ServerSocket(4566)
      serverSocket.localPort == 0
    } catch (_: IOException) {
      true
    }
}
