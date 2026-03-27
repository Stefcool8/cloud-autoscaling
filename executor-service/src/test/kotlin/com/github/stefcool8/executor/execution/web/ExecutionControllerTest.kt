package com.github.stefcool8.executor.execution.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.service.ExecutionService
import com.github.stefcool8.executor.execution.web.dto.ExecutionRequest
import com.github.stefcool8.executor.execution.web.dto.ExecutionResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@WebMvcTest(ExecutionController::class)
class ExecutionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @MockitoBean
    private lateinit var executionService: ExecutionService

    @MockitoBean
    private lateinit var jpaMappingContext: JpaMetamodelMappingContext

    @Test
    fun `POST to executions should return 201 Created`() {
        val request = ExecutionRequest(script = "echo hello", cpuCount = BigDecimal("1.0"))
        val mockResponse = ExecutionResponse(
            id = UUID.randomUUID(),
            script = request.script,
            cpuCount = request.cpuCount,
            status = ExecutionStatus.QUEUED,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

        `when`(executionService.submitExecution(request)).thenReturn(mockResponse)

        mockMvc.perform(post("/api/executions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(mockResponse.id.toString()))
            .andExpect(jsonPath("$.status").value("QUEUED"))
    }

    @Test
    fun `POST to executions with blank script should return 400 Bad Request`() {
        // Test RestExceptionHandler
        val badRequest = ExecutionRequest(script = "", cpuCount = BigDecimal("1.0"))

        mockMvc.perform(post("/api/executions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(badRequest)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.details.script").value("Script cannot be empty"))
    }

    @Test
    fun `GET execution by ID should return 200 OK`() {
        val executionId = UUID.randomUUID()
        val mockResponse = ExecutionResponse(
            id = executionId,
            script = "pwd",
            cpuCount = BigDecimal("2.0"),
            status = ExecutionStatus.IN_PROGRESS,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

        `when`(executionService.getExecutionStatus(executionId)).thenReturn(mockResponse)

        mockMvc.perform(get("/api/executions/$executionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(executionId.toString()))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
    }

    @Test
    fun `GET execution with invalid ID should return 404 Not Found`() {
        val invalidId = UUID.randomUUID()

        `when`(executionService.getExecutionStatus(invalidId))
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Execution with ID $invalidId not found"))

        mockMvc.perform(get("/api/executions/$invalidId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Execution with ID $invalidId not found"))
    }
}
