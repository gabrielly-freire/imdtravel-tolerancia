package br.ufrn.imd.imdtravel.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.config.RestClientConfig;
import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Component
public class ExchangeHttpClient implements ExchangeClient {

    private final RestClient http;
    private final CircuitBreaker convertCb;

    public ExchangeHttpClient(
            RestClientConfig restClientConfig,
            ResilienceProperties resilienceProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${imdtravel.exchange-url}") String baseUrl) {
        this.http = restClientConfig.createClient(baseUrl, resilienceProperties.timeoutConvertMs());
        this.convertCb = circuitBreakerRegistry.circuitBreaker("convert");
    }

    @Override
    public ConvertResponse convert() {
        return convertCb.executeSupplier(() -> http.get()
                .uri("/convert")
                .retrieve()
                .body(ConvertResponse.class));
    }
}
