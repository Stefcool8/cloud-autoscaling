package com.github.stefcool8.executor.infrastructure.kubernetes

import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import io.fabric8.kubernetes.api.model.ContainerStateBuilder
import io.fabric8.kubernetes.api.model.ContainerStateWaitingBuilder
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.time.Duration
import java.util.*

@EnableKubernetesMockClient(crud = true)
class KubernetesPodInformerTest {

    private lateinit var client: KubernetesClient
    private lateinit var executionRepository: ExecutionRepository
    private lateinit var podInformer: KubernetesPodInformer

    private val executionId = UUID.randomUUID()
    private val jobName = "exec-$executionId"
    private val podName = "pod-$executionId"

    private lateinit var mockExecution: Execution

    @BeforeEach
    fun setUp() {
        executionRepository = mock(ExecutionRepository::class.java)
        podInformer = KubernetesPodInformer(client, executionRepository)

        mockExecution = Execution(
            id = executionId,
            script = "echo 'testing'",
            cpuCount = BigDecimal("0.5"),
            status = ExecutionStatus.QUEUED
        )
        `when`(executionRepository.findById(executionId)).thenReturn(Optional.of(mockExecution))

        podInformer.startInformer()
    }

    @AfterEach
    fun tearDown() {
        podInformer.stopInformer()
    }

    @Test
    fun `should update DB to IN_PROGRESS when Pod transitions to Running`() {
        val runningPod = PodBuilder()
            .withNewMetadata()
            .withName(podName)
            .addToLabels("job-name", jobName)
            .endMetadata()
            .withNewStatus()
            .withPhase("Running")
            .endStatus()
            .build()

        client.pods().inNamespace("default").resource(runningPod).create()

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(executionRepository, atLeastOnce()).save(argThat {
                it.status == ExecutionStatus.IN_PROGRESS
            })
        }
    }

    @Test
    fun `should update DB to FINISHED when Pod transitions to Succeeded`() {
        val succeededPod = PodBuilder()
            .withNewMetadata()
            .withName(podName)
            .addToLabels("job-name", jobName)
            .endMetadata()
            .withNewStatus()
            .withPhase("Succeeded")
            .endStatus()
            .build()

        client.pods().inNamespace("default").resource(succeededPod).create()

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(executionRepository, atLeastOnce()).save(argThat {
                it.status == ExecutionStatus.FINISHED
            })
        }
    }

    @Test
    fun `should catch ImagePullBackOff edge case and fail execution instantly`() {
        val crashedPod = PodBuilder()
            .withNewMetadata()
            .withName(podName)
            .addToLabels("job-name", jobName)
            .endMetadata()
            .withNewStatus()
            .withPhase("Pending")
            .withContainerStatuses(
                ContainerStatusBuilder()
                    .withState(
                        ContainerStateBuilder()
                            .withWaiting(
                                ContainerStateWaitingBuilder()
                                    .withReason("ImagePullBackOff")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .endStatus()
            .build()

        client.pods().inNamespace("default").resource(crashedPod).create()

        // The Informer should intercept the ImagePullBackOff and forcefully mark it FAILED
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(executionRepository, atLeastOnce()).save(argThat {
                it.status == ExecutionStatus.FAILED &&
                        it.output?.contains("Could not pull container image") == true
            })
        }
    }
}
