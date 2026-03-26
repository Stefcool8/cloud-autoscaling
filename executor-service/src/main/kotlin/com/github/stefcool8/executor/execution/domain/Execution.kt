package com.github.stefcool8.executor.execution.domain

import jakarta.persistence.*
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "executions")
@EntityListeners(AuditingEntityListener::class)
class Execution(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, columnDefinition = "TEXT")
    var script: String,

    @Column(name = "cpu_count", nullable = false, precision = 5, scale = 2)
    var cpuCount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ExecutionStatus = ExecutionStatus.QUEUED,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now(),

    @Column(columnDefinition = "TEXT")
    var output: String? = null
)
