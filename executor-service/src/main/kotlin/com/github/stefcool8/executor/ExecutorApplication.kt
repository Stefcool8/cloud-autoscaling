package com.github.stefcool8.executor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class ExecutorServiceApplication

fun main(args: Array<String>) {
    runApplication<ExecutorServiceApplication>(*args)
}
