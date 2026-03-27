package com.github.stefcool8.executor.infrastructure.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Duration

@Configuration
@Profile("docker")
class DockerConfig {

    @Bean
    fun dockerClient(): DockerClient {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()

        return DockerClientImpl.getInstance(config, httpClient)
    }
}
