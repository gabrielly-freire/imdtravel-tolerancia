package br.ufrn.imd.imdtravel.config;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.ufrn.imd.imdtravel.exceptions.NotFoundException;
import br.ufrn.imd.imdtravel.exceptions.SoldOutException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Configura um Circuit Breaker isolado por endpoint de dependência.
 */
@Configuration
public class ResilienceConfig {

    public static final List<String> ENDPOINTS = List.of("flight", "convert", "sell", "bonus");

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceProperties props, JsonLogger logger) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(props.cbSlidingWindowSize())
                .minimumNumberOfCalls(props.cbMinimumNumberOfCalls())
                .failureRateThreshold((float) props.cbFailureRateThreshold())
                .waitDurationInOpenState(Duration.ofMillis(props.cbWaitDurationOpenMs()))
                .permittedNumberOfCallsInHalfOpenState(props.cbHalfOpenPermittedCalls())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .ignoreExceptions(NotFoundException.class, SoldOutException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        for (String endpoint : ENDPOINTS) {
            CircuitBreaker cb = registry.circuitBreaker(endpoint);
            cb.getEventPublisher().onStateTransition(event -> logger.logCircuitBreakerTransition(
                    endpoint,
                    event.getStateTransition().getFromState().name(),
                    event.getStateTransition().getToState().name()));
            cb.getEventPublisher().onCallNotPermitted(
                    event -> logger.logCircuitBreakerRejected(CorrelationContext.getCid(), endpoint));
        }
        return registry;
    }
}
