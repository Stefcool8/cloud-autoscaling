package com.github.stefcool8.executor.execution.web.dto

import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExecutionResponse(
    val id: UUID,
    val script: String,
    val cpuCount: BigDecimal,
    val status: ExecutionStatus,
    val output: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun fromEntity(entity: Execution) = ExecutionResponse(
            id = entity.id,
            script = entity.script,
            cpuCount = entity.cpuCount,
            status = entity.status,
            output = entity.output,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
