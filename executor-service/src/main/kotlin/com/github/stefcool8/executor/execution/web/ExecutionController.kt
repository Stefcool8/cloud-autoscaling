package com.github.stefcool8.executor.execution.web

import com.github.stefcool8.executor.execution.service.ExecutionService
import com.github.stefcool8.executor.execution.web.dto.ExecutionRequest
import com.github.stefcool8.executor.execution.web.dto.ExecutionResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/executions")
class ExecutionController(
    private val executionService: ExecutionService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createExecution(@Valid @RequestBody request: ExecutionRequest): ExecutionResponse {
        return executionService.submitExecution(request)
    }

    @GetMapping("/{id}")
    fun getExecution(@PathVariable id: UUID): ExecutionResponse {
        return executionService.getExecutionStatus(id)
    }

    @GetMapping
    fun listExecutions(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable
    ): Page<ExecutionResponse> {
        return executionService.getAllExecutions(pageable)
    }
}
