package br.ufrn.imd.imdtravel.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufrn.imd.imdtravel.config.ResilienceConfig;
import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Expõe o estado operacional dos circuit breakers e parâmetros de resiliência (esquema provisório).
 */
@RestController
public class StatusController {

    private final CircuitBreakerRegistry registry;
    private final ResilienceProperties props;

    public StatusController(CircuitBreakerRegistry registry, ResilienceProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> circuits = new LinkedHashMap<>();
        boolean degraded = false;

        for (String endpoint : ResilienceConfig.ENDPOINTS) {
            CircuitBreaker cb = registry.circuitBreaker(endpoint);
            CircuitBreaker.State state = cb.getState();
            if (state != CircuitBreaker.State.CLOSED) {
                degraded = true;
            }
            CircuitBreaker.Metrics metrics = cb.getMetrics();
            Map<String, Object> cbInfo = new LinkedHashMap<>();
            cbInfo.put("state", state.name());
            cbInfo.put("failureRate", metrics.getFailureRate());
            cbInfo.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
            cbInfo.put("failedCalls", metrics.getNumberOfFailedCalls());
            circuits.put(endpoint, cbInfo);
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("timeoutFlightMs", props.timeoutFlightMs());
        config.put("timeoutConvertMs", props.timeoutConvertMs());
        config.put("timeoutSellMs", props.timeoutSellMs());
        config.put("timeoutBonusMs", props.timeoutBonusMs());
        config.put("deadlineTotalMs", props.deadlineTotalMs());
        config.put("cbFailureRateThreshold", props.cbFailureRateThreshold());
        config.put("cbSlidingWindowSize", props.cbSlidingWindowSize());
        config.put("cbMinimumNumberOfCalls", props.cbMinimumNumberOfCalls());
        config.put("cbWaitDurationOpenMs", props.cbWaitDurationOpenMs());
        config.put("cbHalfOpenPermittedCalls", props.cbHalfOpenPermittedCalls());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", degraded ? "degraded" : "ok");
        response.put("circuits", circuits);
        response.put("config", config);
        return response;
    }
}
