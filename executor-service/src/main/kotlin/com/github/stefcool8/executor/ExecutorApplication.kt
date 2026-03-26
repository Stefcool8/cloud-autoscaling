package com.github.stefcool8.executor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ExecutorServiceApplication

fun main(args: Array<String>) {
    runApplication<ExecutorServiceApplication>(*args)
}
