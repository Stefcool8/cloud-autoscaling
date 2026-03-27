package com.github.stefcool8.executor.infrastructure.kubernetes

import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors

// This annotation automatically starts a fake K8s API server on a random port
// and injects a pre-configured 'client' into this test class
@EnableKubernetesMockClient(crud = true)
class KubernetesExecutorClientImplTest {
    private lateinit var client: KubernetesClient

    private lateinit var executionRepository: ExecutionRepository
    private lateinit var kubernetesExecutorClient: KubernetesExecutorClientImpl

    @BeforeEach
    fun setUp() {
        executionRepository = mock(ExecutionRepository::class.java)
        kubernetesExecutorClient = KubernetesExecutorClientImpl(client, executionRepository)
    }

    @Test
    fun `should create job, wait for completion, fetch logs, and update status to FINISHED`() {
        val executionId = UUID.randomUUID()
        val execution = Execution(
            id = executionId,
            script = "echo 'mock cluster'",
            cpuCount = BigDecimal("0.5"),
            status = ExecutionStatus.QUEUED
        )
        val jobName = "exec-$executionId"

        `when`(executionRepository.findById(executionId)).thenReturn(Optional.of(execution))

        // Run the execute method in a background thread.
        // This is done because execute() contains 'waitUntilCondition',
        // which will block the main thread until the Job finishes.
        val executorService = Executors.newSingleThreadExecutor()
        val executionFuture = executorService.submit {
            kubernetesExecutorClient.execute(execution)
        }

        // Wait until Job is created in the mock K8s API
        await().atMost(Duration.ofSeconds(5)).until {
            client.batch().v1().jobs().inNamespace("default").withName(jobName).get() != null
        }

        val podName = "pod-$executionId"
        val mockPod = PodBuilder()
            .withNewMetadata()
            .withName(podName)
            .addToLabels("job-name", jobName)
            .endMetadata()
            .build()

        client.pods().inNamespace("default").resource(mockPod).create()

        // Update the Job status to "Succeeded = 1" to unblock the waitUntilCondition
        val job = client.batch().v1().jobs().inNamespace("default").withName(jobName).get()

        // The mock server doesn't auto-initialize the status object, so we do it manually
        if (job.status == null) {
            job.status = io.fabric8.kubernetes.api.model.batch.v1.JobStatus()
        }
        job.status.succeeded = 1

        client.batch().v1().jobs().inNamespace("default").resource(job).updateStatus()

        executionFuture.get()

        assertEquals(ExecutionStatus.FINISHED, execution.status)
        verify(executionRepository, atLeastOnce()).save(execution)
    }
}
