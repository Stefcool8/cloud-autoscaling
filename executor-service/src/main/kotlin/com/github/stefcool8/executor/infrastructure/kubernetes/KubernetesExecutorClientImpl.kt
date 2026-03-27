package com.github.stefcool8.executor.infrastructure.kubernetes

import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import com.github.stefcool8.executor.execution.service.RemoteExecutorClient
import io.fabric8.kubernetes.api.model.DeletionPropagation
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@Profile("kubernetes") // Only load this bean if the "kubernetes" profile is active
class KubernetesExecutorClientImpl(
    private val kubernetesClient: KubernetesClient,
    private val executionRepository: ExecutionRepository
) : RemoteExecutorClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val namespace = "default"

    override fun execute(execution: Execution) {
        log.info("Starting execution ${execution.id} on Kubernetes...")
        updateExecutionRecord(execution.id, ExecutionStatus.IN_PROGRESS)

        val jobName = "exec-${execution.id}"
        var finalStatus = ExecutionStatus.FAILED
        var finalOutput: String? = null

        try {
            // Define the K8s Job
            val cpuQuantity = Quantity(execution.cpuCount.toString())

            val job = JobBuilder()
                .withNewMetadata().withName(jobName).endMetadata()
                .withNewSpec()
                .withBackoffLimit(0) // Do not retry if the script fails
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withName("alpine-executor")
                .withImage("alpine:latest")
                .withCommand("/bin/sh", "-c", execution.script)
                .withNewResources()
                // Map user CPU to K8s limits
                .addToRequests("cpu", cpuQuantity)
                .addToLimits("cpu", cpuQuantity)
                .endResources()
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build()

            // Submit the Job to the cluster
            kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(job).create()
            log.info("Job $jobName created in cluster.")

            // Wait for the Job to finish (Max 5 minutes)
            val completedJob = kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName)
                .waitUntilCondition({ j ->
                    val succeeded = j?.status?.succeeded ?: 0
                    val failed = j?.status?.failed ?: 0
                    succeeded > 0 || failed > 0
                }, 5, TimeUnit.MINUTES)

            val didSucceed = (completedJob?.status?.succeeded ?: 0) > 0
            finalStatus = if (didSucceed) ExecutionStatus.FINISHED else ExecutionStatus.FAILED

            // Fetch logs from the Pod associated with this Job
            val pods = kubernetesClient.pods().inNamespace(namespace).withLabel("job-name", jobName).list().items
            val podName = pods.firstOrNull()?.metadata?.name

            finalOutput = if (podName != null) {
                kubernetesClient.pods().inNamespace(namespace).withName(podName).log
            } else {
                "Error: Could not find pod to extract logs."
            }

            log.info("Execution ${execution.id} completed with status $finalStatus")

        } catch (e: Exception) {
            log.error("System error while running K8s job for execution ${execution.id}", e)
            finalOutput = "System Error: ${e.message}"
            finalStatus = ExecutionStatus.FAILED
        } finally {
            // Delete the Job (and its pods) so the cluster doesn't fill up
            try {
                // DeletionPropagation.BACKGROUND ensures K8s also deletes the Pods created by the Job
                kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName)
                    .withPropagationPolicy(DeletionPropagation.BACKGROUND)
                    .delete()
                log.info("Cleaned up Job $jobName")
            } catch (cleanupEx: Exception) {
                log.warn("Failed to cleanup K8s Job $jobName", cleanupEx)
            }

            // Save final state to DB
            updateExecutionRecord(execution.id, finalStatus, finalOutput?.trim())
        }
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
