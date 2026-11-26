package br.ufrn.imd.imdtravel.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.config.RestClientConfig;
import br.ufrn.imd.imdtravel.dtos.clients.BonusRequest;
import br.ufrn.imd.imdtravel.dtos.clients.BonusResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Component
public class FidelityHttpClient implements FidelityClient {

    private final RestClient http;
    private final CircuitBreaker bonusCb;

    public FidelityHttpClient(
            RestClientConfig restClientConfig,
            ResilienceProperties resilienceProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${imdtravel.fidelity-url}") String baseUrl) {
        this.http = restClientConfig.createClient(baseUrl, resilienceProperties.timeoutBonusMs());
        this.bonusCb = circuitBreakerRegistry.circuitBreaker("bonus");
    }

    @Override
    public BonusResponse bonus(String user, int bonus) {
        return bonusCb.executeSupplier(() -> http.post()
                .uri("/bonus")
                .body(new BonusRequest(user, bonus))
                .retrieve()
                .body(BonusResponse.class));
    }
}
