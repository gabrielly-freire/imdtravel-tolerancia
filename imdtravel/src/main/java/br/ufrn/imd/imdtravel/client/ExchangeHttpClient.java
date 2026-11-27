package br.ufrn.imd.imdtravel.client;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.config.RestClientConfig;
import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;
import br.ufrn.imd.imdtravel.exceptions.InvalidRateException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Component
public class ExchangeHttpClient implements ExchangeClient {

    private final RestClient http;
    private final CircuitBreaker convertCb;
    private final BigDecimal minRate;
    private final BigDecimal maxRate;

    public ExchangeHttpClient(
            RestClientConfig restClientConfig,
            ResilienceProperties resilienceProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${imdtravel.exchange-url}") String baseUrl,
            @Value("${imdtravel.rate.min:5.0}") BigDecimal minRate,
            @Value("${imdtravel.rate.max:6.0}") BigDecimal maxRate) {
        this.minRate = minRate;
        this.maxRate = maxRate;
        this.http = restClientConfig.createClient(baseUrl, resilienceProperties.timeoutConvertMs());
        this.convertCb = circuitBreakerRegistry.circuitBreaker("convert");
    }

    @Override
    public ConvertResponse convert() {
        // A validação fica dentro do circuit breaker: taxa errada conta como falha do endpoint.
        return convertCb.executeSupplier(this::getValidRate);
    }

    private ConvertResponse getValidRate() {
        ConvertResponse response;
        try {
            response = http.get()
                    .uri("/convert")
                    .retrieve()
                    .body(ConvertResponse.class);
        } catch (ResourceAccessException | RestClientResponseException e) {
            throw e;
        } catch (RestClientException e) {
            throw new InvalidRateException("resposta do /convert ilegível", e);
        }

        if (response == null || response.rate() == null
                || response.rate().compareTo(minRate) < 0 || response.rate().compareTo(maxRate) > 0) {
            throw new InvalidRateException("taxa ausente ou fora de [" + minRate + ", " + maxRate + "]", null);
        }
        return response;
    }
}
