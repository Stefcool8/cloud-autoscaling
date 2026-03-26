package com.github.stefcool8.executor.execution.web.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

data class ExecutionRequest(
    @field:NotBlank(message = "Script cannot be empty")
    val script: String,

    @field:DecimalMin(value = "0.1", message = "CPU count must be at least 0.1")
    val cpuCount: BigDecimal
)
