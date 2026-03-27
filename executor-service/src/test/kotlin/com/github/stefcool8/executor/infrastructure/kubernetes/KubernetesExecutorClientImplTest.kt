package com.github.stefcool8.executor.infrastructure.kubernetes

import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.util.*

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
    fun `should successfully create job in cluster and return immediately`() {
        val executionId = UUID.randomUUID()
        val execution = Execution(
            id = executionId,
            script = "echo 'fire and forget'",
            cpuCount = BigDecimal("0.5"),
            status = ExecutionStatus.QUEUED
        )

        kubernetesExecutorClient.execute(execution)

        // The Job was created in the mock K8s API
        val jobName = "exec-$executionId"
        val createdJob = client.batch().v1().jobs().inNamespace("default").withName(jobName).get()

        assertNotNull(createdJob, "Job should have been created in the cluster")
    }
}
