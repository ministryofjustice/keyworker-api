package uk.gov.justice.digital.hmpps.gradle

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Abstract gradle task to connect a local port to an AWS resource in Cloud Platform,
 * such as an RDS database or ElastiCache Redis
 */
@DisableCachingByDefault
abstract class PortForwardTask : CloudPlatformTask() {
  init {
    description = "Connect a local port to an AWS resource in Cloud Platform"
  }

  open var secretName: String? = null
    @Input
    get

  open var remotePort: Int? = null
    @Input
    get

  @Optional
  open var localPort: Int? = 12345
    @Input
    get

  @Option(option = "port", description = "Local port to open (defaults to remote port)")
  fun setLocalPortString(port: String): PortForwardTask {
    this.localPort = port.toIntOrNull() ?: throw IllegalArgumentException("Invalid port")
    return this
  }

  @Optional
  open var podName: String? = null
    @Input
    get
    @Option(option = "pod-name", description = "Name of pod used for port-forwarding (generated if not supplied)")
    set

  @TaskAction
  fun taskAction() {
    val secretName = secretName ?: throw IllegalArgumentException("secretName must be provided")
    val remotePort = remotePort ?: throw IllegalArgumentException("remotePort must be provided")
    if (localPort == null) {
      localPort = remotePort
    }
    val localPort = localPort ?: throw IllegalStateException("unreachable")
    if (podName == null) {
      podName = generateRandomName("port-forward")
    }
    val podName = podName ?: throw IllegalStateException("unreachable")

    logger.info("Will connect to AWS resource in $environment to local port $localPort")

    val secret = getSecret(secretName)
      ?: throw IllegalArgumentException("Secret `$secretName` not found")
    val remoteHost = getRemoteHostFromSecret(secret)

    launchPortForwardingPod(podName, remoteHost, remotePort)
    awaitPodReadiness(podName)
    val portForwardProcess = portForward(podName)
    showUsageInstructions(secret)
    try {
      portForwardProcess.waitFor()
    } catch (e: InterruptedException) {
      // NB: gradle disconnects loggers and output so nothing will be seen
      logger.info("Disconnecting port-forwarding because of interruption")
    } finally {
      portForwardProcess.destroy()
      deletePod(podName)
    }
  }

  open fun showUsageInstructions(secret: Map<String, String>) {
    println("Close port-forwarding connection with Ctrl-C.")
  }

  abstract fun getRemoteHostFromSecret(secret: Map<String, String>): String

  private fun launchPortForwardingPod(podName: String, remoteHost: String, remotePort: Int) {
    logger.info("Launching port-forwarding pod $podName in $namespace")
    ProcessBuilder(
      "kubectl", "--namespace", namespace,
      "run", podName,
      "--image=ministryofjustice/port-forward", "--image-pull-policy=Always", "--restart=Never",
      "--env", "REMOTE_HOST=$remoteHost", "--env", "REMOTE_PORT=$remotePort", "--env", "LOCAL_PORT=$remotePort",
      "--port=$remotePort",
    )
      .start()
      .waitFor()
    logger.info("Launched port-forwarding pod $podName in $namespace")
  }

  private fun awaitPodReadiness(podName: String) {
    logger.info("Waiting for pod $podName readiness in $namespace")
    ProcessBuilder(
      "kubectl", "--namespace", namespace,
      "wait", "pod/$podName",
      "--for", "condition=Ready", "--timeout=60s",
    )
      .start()
      .waitFor()
    logger.info("Port-forwarding pod $podName is ready in $namespace")
  }

  private fun portForward(podName: String): Process {
    logger.info("Starting port-forwarding from local port $localPort to $podName port $remotePort in $namespace")
    return ProcessBuilder(
      "kubectl", "--namespace", namespace,
      "port-forward", "pod/$podName",
      "$localPort:$remotePort",
    )
      .start()
      .also {
        logger.info("Port-forwarding on process ID ${it.pid()}")
      }
  }

  private fun deletePod(podName: String) {
    logger.info("Deleting port-forwarding pod $podName in $namespace")
    ProcessBuilder(
      "kubectl", "--namespace", namespace,
      "delete", "pod", podName,
      "--wait=false",
    )
      .start()
      .waitFor()
    logger.info("Deleted port-forwarding pod $podName in $namespace")
  }
}
