package zw.ac.uz.emhare.admissions.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

/** Preserves the authenticated request identity across circuit-breaker worker threads. @author Tinashe K */
@Configuration(proxyBeanMethods = false)
public class AdmissionsCircuitBreakerSecurityContextConfiguration {

    @Bean(name = "admissionsCircuitBreakerExecutor", destroyMethod = "shutdown")
    ExecutorService admissionsCircuitBreakerExecutor() {
        ExecutorService workerExecutor = Executors.newFixedThreadPool(
                4, Thread.ofPlatform().name("admissions-http-", 0).factory());
        return new DelegatingSecurityContextExecutorService(workerExecutor);
    }

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> admissionsCircuitBreakerExecutorCustomizer(
            ExecutorService admissionsCircuitBreakerExecutor) {
        return factory -> factory.configureExecutorService(admissionsCircuitBreakerExecutor);
    }
}
