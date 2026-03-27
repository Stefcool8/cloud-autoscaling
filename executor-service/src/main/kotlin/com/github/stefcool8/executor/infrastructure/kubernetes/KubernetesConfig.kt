package com.github.stefcool8.executor.infrastructure.kubernetes

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("kubernetes") // Only load this bean if the "kubernetes" profile is active
class KubernetesConfig {

    @Bean
    fun kubernetesClient(): KubernetesClient {
        // Auto-detects local ~/.kube/config or in-cluster ServiceAccount tokens
        return KubernetesClientBuilder().build()
    }
}
