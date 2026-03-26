package com.github.stefcool8.executor.execution.service

import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import com.github.stefcool8.executor.execution.web.dto.ExecutionRequest
import com.github.stefcool8.executor.execution.web.dto.ExecutionResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ExecutionService(
    private val executionRepository: ExecutionRepository,
    private val remoteExecutorClient: RemoteExecutorClient
) {
    @Transactional
    fun submitExecution(request: ExecutionRequest): ExecutionResponse {
        // Defaults to 'QUEUED'
        val newExecution = Execution(
            script = request.script,
            cpuCount = request.cpuCount
        )

        val savedExecution = executionRepository.save(newExecution)

        // (Future phase: Trigger Kubernetes/Docker here!)

        // This hands the task to the "RemoteExec-" thread pool
        remoteExecutorClient.execute(savedExecution)

        return ExecutionResponse.fromEntity(savedExecution)
    }

    fun getExecutionStatus(id: UUID): ExecutionResponse {
        val execution = executionRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Execution with ID $id not found")
        }

        return ExecutionResponse.fromEntity(execution)
    }

    @Transactional(readOnly = true)
    fun getAllExecutions(pageable: Pageable): Page<ExecutionResponse> {
        return executionRepository.findAll(pageable)
            .map { ExecutionResponse.fromEntity(it) }
    }
}
