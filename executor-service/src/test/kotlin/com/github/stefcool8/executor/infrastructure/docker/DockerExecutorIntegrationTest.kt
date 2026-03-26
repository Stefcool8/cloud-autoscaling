package com.github.stefcool8.executor.infrastructure.docker

import com.github.stefcool8.executor.config.IntegrationTestBase
import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import com.github.stefcool8.executor.execution.service.RemoteExecutorClient
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Duration

class DockerExecutorIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var executionRepository: ExecutionRepository

    @Autowired
    private lateinit var remoteExecutorClient: RemoteExecutorClient

    @Test
    fun `should execute script successfully and update status to FINISHED`() {
        val newExecution = Execution(
            script = "echo 'Awaitility is awesome!' && sleep 2",
            cpuCount = BigDecimal("0.5")
        )
        val savedExecution = executionRepository.save(newExecution)

        // Trigger the async execution
        remoteExecutorClient.execute(savedExecution)

        // Wait for the async process to finish using Awaitility
        await()
            .atMost(Duration.ofSeconds(15)) // Give Docker max 15 seconds to pull Alpine and run
            .pollInterval(Duration.ofMillis(500)) // Check the database every half-second
            .untilAsserted {
                // Fetch the latest state from the database
                val updatedExecution = executionRepository.findById(savedExecution.id).get()

                // If these assertions fail, Awaitility waits and tries again until the timeout
                assertEquals(ExecutionStatus.FINISHED, updatedExecution.status)
                assertTrue(updatedExecution.output?.contains("Awaitility is awesome!") == true)
            }
    }

    @Test
    fun `should mark execution as FAILED and capture stderr when script crashes`() {
        // A script that intentionally fails (trying to read a file that doesn't exist)
        val badExecution = Execution(
            script = "cat /this/file/does/not/exist.txt",
            cpuCount = BigDecimal("0.5")
        )
        val savedExecution = executionRepository.save(badExecution)

        remoteExecutorClient.execute(savedExecution)

        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted {
                val updatedExecution = executionRepository.findById(savedExecution.id).get()

                assertEquals(ExecutionStatus.FAILED, updatedExecution.status)

                // Alpine Linux outputs this specific error message for missing files
                assertTrue(updatedExecution.output?.contains("No such file or directory") == true)
            }
    }
}
