package com.github.stefcool8.executor.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean(name = ["executorThreadPool"])
    fun asyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        // The minimum number of threads kept alive
        executor.corePoolSize = 5
        // The maximum number of concurrent executions allowed
        executor.maxPoolSize = 10
        // How many tasks can wait in line before we start rejecting them
        executor.queueCapacity = 50
        // Easier to read debugging logs
        executor.setThreadNamePrefix("RemoteExec-")
        executor.initialize()
        return executor
    }
}
