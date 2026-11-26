package br.ufrn.imd.imdtravel.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.config.RestClientConfig;
import br.ufrn.imd.imdtravel.dtos.clients.FlightResponse;
import br.ufrn.imd.imdtravel.dtos.clients.SellRequest;
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.NotFoundException;
import br.ufrn.imd.imdtravel.exceptions.SoldOutException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Component
public class AirlinesHttpClient implements AirlinesClient {

    private final RestClient flightHttp;
    private final RestClient sellHttp;
    private final CircuitBreaker flightCb;
    private final CircuitBreaker sellCb;

    public AirlinesHttpClient(
            RestClientConfig restClientConfig,
            ResilienceProperties resilienceProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${imdtravel.airlineshub-url}") String baseUrl) {
        this.flightHttp = restClientConfig.createClient(baseUrl, resilienceProperties.timeoutFlightMs());
        this.sellHttp = restClientConfig.createClient(baseUrl, resilienceProperties.timeoutSellMs());
        this.flightCb = circuitBreakerRegistry.circuitBreaker("flight");
        this.sellCb = circuitBreakerRegistry.circuitBreaker("sell");
    }

    @Override
    public FlightResponse getFlight(String flight, String day) {
        return flightCb.executeSupplier(() -> flightHttp.get()
                .uri(u -> u.path("/flight").queryParam("flight", flight).queryParam("day", day).build())
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new NotFoundException("voo ou dia inexistente");
                })
                .body(FlightResponse.class));
    }

    @Override
    public SellResponse sell(String flight, String day) {
        return sellCb.executeSupplier(() -> sellHttp.post()
                .uri("/sell")
                .body(new SellRequest(flight, day))
                .retrieve()
                .onStatus(s -> s.value() == 409, (req, res) -> {
                    throw new SoldOutException("sold-out");
                })
                .body(SellResponse.class));
    }
}
