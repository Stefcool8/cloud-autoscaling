package com.github.stefcool8.executor.execution.repository

import com.github.stefcool8.executor.execution.domain.Execution
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExecutionRepository : JpaRepository<Execution, UUID>
