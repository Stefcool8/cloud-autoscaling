package com.github.stefcool8.executor.execution.service

import com.github.stefcool8.executor.execution.domain.Execution
import com.github.stefcool8.executor.execution.domain.ExecutionStatus
import com.github.stefcool8.executor.execution.repository.ExecutionRepository
import com.github.stefcool8.executor.execution.web.dto.ExecutionRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockitoExtension::class)
class ExecutionServiceTest {

    @Mock
    private lateinit var executionRepository: ExecutionRepository

    @InjectMocks
    private lateinit var executionService: ExecutionService

    @Test
    fun `submitExecution should save to repository and return response`() {
        val request = ExecutionRequest(script = "echo 'test'", cpuCount = BigDecimal("1.5"))
        val mockSavedExecution = Execution(
            id = UUID.randomUUID(),
            script = request.script,
            cpuCount = request.cpuCount,
            status = ExecutionStatus.QUEUED
        )

        // What to do when save() is called
        `when`(executionRepository.save(any(Execution::class.java))).thenReturn(mockSavedExecution)

        val response = executionService.submitExecution(request)

        assertNotNull(response.id)
        assertEquals("echo 'test'", response.script)
        assertEquals(BigDecimal("1.5"), response.cpuCount)
        assertEquals(ExecutionStatus.QUEUED, response.status)

        // Verify that the repository's save method was actually called exactly once
        verify(executionRepository).save(any(Execution::class.java))
    }

    @Test
    fun `getExecutionStatus should return execution when found`() {
        val executionId = UUID.randomUUID()
        val mockExecution = Execution(
            id = executionId,
            script = "pwd",
            cpuCount = BigDecimal("0.5"),
            status = ExecutionStatus.IN_PROGRESS
        )

        `when`(executionRepository.findById(executionId)).thenReturn(Optional.of(mockExecution))

        val response = executionService.getExecutionStatus(executionId)

        assertEquals(executionId, response.id)
        assertEquals(ExecutionStatus.IN_PROGRESS, response.status)
    }

    @Test
    fun `getExecutionStatus should throw 404 when not found`() {
        val nonExistentId = UUID.randomUUID()
        `when`(executionRepository.findById(nonExistentId)).thenReturn(Optional.empty())

        val exception = assertThrows(ResponseStatusException::class.java) {
            executionService.getExecutionStatus(nonExistentId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
        assertTrue(exception.reason!!.contains("not found"))
    }
}
