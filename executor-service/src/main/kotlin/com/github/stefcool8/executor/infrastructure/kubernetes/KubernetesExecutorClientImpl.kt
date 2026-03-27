package com.github.stefcool8.executor.infrastructure.kubernetes

import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import com.github.stefcool8.executor.execution.service.RemoteExecutorClient
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("kubernetes")
class KubernetesExecutorClientImpl(
    private val kubernetesClient: KubernetesClient,
    private val executionRepository: ExecutionRepository
) : RemoteExecutorClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val namespace = kubernetesClient.namespace ?: "default"

    override fun execute(execution: Execution) {
        log.info("Dispatching execution ${execution.id} to Kubernetes...")
        val jobName = "exec-${execution.id}"

        try {
            val cpuQuantity = Quantity(execution.cpuCount.toString())

            val job = JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(0)
                .withTtlSecondsAfterFinished(30)
                .withNewTemplate()
                .withNewMetadata()
                // The informer relies on this label to track the pod
                .addToLabels("job-name", jobName)
                .endMetadata()
                .withNewSpec()
                .withAutomountServiceAccountToken(false)
                .addNewContainer()
                .withName("alpine-executor")
                .withImage("alpine:latest")
                .withCommand("/bin/sh", "-c", execution.script)
                .withNewResources()
                .addToRequests("cpu", cpuQuantity)
                .addToLimits("cpu", cpuQuantity)
                .endResources()
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build()

            kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(job).create()
            log.info("Job $jobName successfully submitted. Informer will handle lifecycle.")

        } catch (e: Exception) {
            log.error("Failed to submit K8s job for execution ${execution.id}", e)

            // If the cluster is unreachable, the Informer will never know about this job.
            // Manually fail it here so it doesn't get stuck in QUEUED forever.
            executionRepository.findById(execution.id).ifPresent {
                it.status = ExecutionStatus.FAILED
                it.output = "Failed to dispatch to Kubernetes: ${e.message}"
                executionRepository.save(it)
            }
        }
    }
}
