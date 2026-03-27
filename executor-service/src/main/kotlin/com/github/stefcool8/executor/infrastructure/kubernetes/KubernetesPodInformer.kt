package com.github.stefcool8.executor.infrastructure.kubernetes

import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.*

@Component
@Profile("kubernetes")
class KubernetesPodInformer(
    private val client: KubernetesClient,
    private val executionRepository: ExecutionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val namespace = client.namespace ?: "default"
    private lateinit var podInformer: SharedIndexInformer<Pod>

    @PostConstruct
    fun startInformer() {
        log.info("Starting Kubernetes Pod Informer in namespace '$namespace'...")

        // 30,000ms for the resync period
        podInformer = client.pods().inNamespace(namespace).inform(object : ResourceEventHandler<Pod> {
            override fun onAdd(pod: Pod) {
                handlePodStateChange(pod)
            }

            override fun onUpdate(oldPod: Pod, newPod: Pod) {
                handlePodStateChange(newPod)
            }

            override fun onDelete(pod: Pod, deletedFinalStateUnknown: Boolean) {
            }
        }, 30 * 1000L)
    }

    @PreDestroy
    fun stopInformer() {
        log.info("Shutting down Kubernetes Pod Informer...")
        if (this::podInformer.isInitialized) {
            podInformer.close()
        }
    }

    private fun handlePodStateChange(pod: Pod) {
        // Only Pods created by our Job
        val jobName = pod.metadata?.labels?.get("job-name") ?: return
        if (!jobName.startsWith("exec-")) return

        // Extract the Execution ID
        val executionIdStr = jobName.removePrefix("exec-")
        val executionId = try {
            UUID.fromString(executionIdStr)
        } catch (_: IllegalArgumentException) {
            log.warn("Found pod with malformed execution ID in label: $jobName")
            return
        }

        val phase = pod.status?.phase ?: "Unknown"
        val containerStatus = pod.status?.containerStatuses?.firstOrNull()

        // If K8s can't pull the image, the Pod phase stays "Pending" forever (needs catching)
        val waitReason = containerStatus?.state?.waiting?.reason
        if (waitReason == "ImagePullBackOff" || waitReason == "ErrImagePull") {
            log.error("Execution $executionId failed due to $waitReason")
            updateDbStatus(executionId,
                ExecutionStatus.FAILED,
                "System Error: Could not pull container image ($waitReason)."
            )
            return
        }

        // Standard State Transitions
        when (phase) {
            "Pending" -> updateDbStatus(executionId, ExecutionStatus.QUEUED)
            "Running" -> updateDbStatus(executionId, ExecutionStatus.IN_PROGRESS)
            "Succeeded" -> {
                val logs = fetchPodLogs(pod.metadata.name)
                updateDbStatus(executionId, ExecutionStatus.FINISHED, logs)
            }
            "Failed" -> {
                val logs = fetchPodLogs(pod.metadata.name)
                updateDbStatus(executionId, ExecutionStatus.FAILED, logs)
            }
        }
    }

    private fun fetchPodLogs(podName: String): String {
        return try {
            client.pods().inNamespace(namespace).withName(podName).log ?: "No output generated."
        } catch (e: Exception) {
            log.warn("Could not fetch logs for pod $podName", e)
            "Error retrieving logs: ${e.message}"
        }
    }

    private fun updateDbStatus(id: UUID, status: ExecutionStatus, output: String? = null) {
        executionRepository.findById(id).ifPresent { execution ->
            // Prevent spamming the database.
            // Only save if the status actually changed,
            // or if we just received the final output logs.
            val statusChanged = execution.status != status
            val outputAdded = output != null && execution.output == null

            if (statusChanged || outputAdded) {
                execution.status = status
                if (output != null) {
                    execution.output = output.trim()
                }
                executionRepository.save(execution)
                log.info("Execution $id transitioned to $status")
            }
        }
    }
}
