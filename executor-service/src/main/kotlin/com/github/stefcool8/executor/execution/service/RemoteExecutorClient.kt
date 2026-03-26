package com.github.stefcool8.executor.execution.service

import com.github.stefcool8.executor.execution.domain.Execution
import org.springframework.scheduling.annotation.Async

interface RemoteExecutorClient {

    /**
     * Triggers the remote execution of a script.
     * The @Async annotation ensures this runs on the custom thread pool,
     * immediately returning control to the HTTP caller.
     */
    @Async("executorThreadPool")
    fun execute(execution: Execution)
}
