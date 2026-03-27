package com.github.stefcool8.executor.infrastructure.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import com.github.stefcool8.executor.execution.service.RemoteExecutorClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Component
@Profile("docker")
class DockerExecutorClientImpl(
    private val dockerClient: DockerClient,
    private val executionRepository: ExecutionRepository
) : RemoteExecutorClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(execution: Execution) {
        log.info("Starting execution ${execution.id} on Docker...")
        updateExecutionRecord(execution.id, ExecutionStatus.IN_PROGRESS)

        var containerId: String? = null
        var finalStatus = ExecutionStatus.FAILED
        var containerOutput: String? = null

        try {
            dockerClient.pullImageCmd("alpine:latest").start().awaitCompletion()

            // Map CPU count to Docker NanoCPUs (1 CPU = 1,000,000,000 NanoCPUs)
            val nanoCpus = (execution.cpuCount.toDouble() * 1_000_000_000).toLong()

            val container = dockerClient.createContainerCmd("alpine:latest")
                .withCmd("sh", "-c", execution.script) // Run the user's script
                .withHostConfig(HostConfig.newHostConfig().withNanoCPUs(nanoCpus))
                .exec()
            containerId = container.id

            dockerClient.startContainerCmd(containerId).exec()
            log.info("Container $containerId started for execution ${execution.id}")

            // Wait for the container to finish, with timeout
            val exitCode = try {
                dockerClient.waitContainerCmd(containerId)
                    .exec(WaitContainerResultCallback())
                    .awaitStatusCode(5, TimeUnit.MINUTES) // Max 5 minutes per job
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt() // Restore the interrupted status
                log.error("Execution ${execution.id} was interrupted!")
                dockerClient.killContainerCmd(containerId).exec()
                -1
            } catch (_: Exception) {
                log.error("Execution ${execution.id} timed out after 5 minutes!")
                // Force kill the hanging container
                dockerClient.killContainerCmd(containerId).exec()
                -1 // Trigger the FAILED state
            }

            finalStatus = if (exitCode == 0) ExecutionStatus.FINISHED else ExecutionStatus.FAILED
            containerOutput = fetchContainerLogs(containerId)

            log.info("Execution ${execution.id} completed with status $finalStatus")

        } catch (e: Exception) {
            log.error("System error while running execution ${execution.id}", e)
            containerOutput = "System Error: ${e.message}"
            finalStatus = ExecutionStatus.FAILED
        } finally {
            // Cleanup the container
            containerId?.let {
                try {
                    dockerClient.removeContainerCmd(it).withForce(true).exec()
                } catch (cleanupEx: Exception) {
                    log.warn("Failed to cleanup container $it", cleanupEx)
                }
            }

            // Save the final status AND the output logs to the database
            updateExecutionRecord(execution.id, finalStatus, containerOutput)
        }
    }

    /**
     * Helper to stream logs from the Docker daemon into a String.
     * For production, if scripts output gigabytes of logs, it would be better
     * to stream this directly to an S3 bucket instead of keeping it in memory.
     */
    private fun fetchContainerLogs(containerId: String): String {
        val logBuilder = java.lang.StringBuilder()

        val logCallback = object : ResultCallback.Adapter<Frame>() {
            override fun onNext(frame: Frame) {
                // frame.payload contains the raw byte array of the log line
                logBuilder.append(String(frame.payload, StandardCharsets.UTF_8))
            }
        }

        dockerClient.logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withTailAll()
            .exec(logCallback)
            .awaitCompletion()

        return logBuilder.toString().trim()
    }

    private fun updateExecutionRecord(id: java.util.UUID, status: ExecutionStatus, output: String? = null) {
        executionRepository.findById(id).ifPresent {
            it.status = status
            if (output != null) {
                it.output = output
            }
            executionRepository.save(it)
        }
    }
}
