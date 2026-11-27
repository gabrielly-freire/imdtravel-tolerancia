package br.ufrn.imd.imdtravel.client;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.config.RestClientConfig;
import br.ufrn.imd.imdtravel.dtos.clients.FlightResponse;
import br.ufrn.imd.imdtravel.dtos.clients.SellRequest;
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.NotFoundException;
import br.ufrn.imd.imdtravel.exceptions.SaleUnknownException;
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
        return sellCb.executeSupplier(() -> postSell(flight, day));
    }

    /**
     * Uma única tentativa: o AirlinesHub não tem chave de idempotência, então repetir o POST
     * pode vender um segundo assento.
     */
    private SellResponse postSell(String flight, String day) {
        ResponseEntity<SellResponse> response;
        try {
            response = sellHttp.post()
                    .uri("/sell")
                    .body(new SellRequest(flight, day))
                    .retrieve()
                    .onStatus(s -> s.value() == 409, (req, res) -> {
                        throw new SoldOutException("sold-out");
                    })
                    .toEntity(SellResponse.class);
        } catch (HttpClientErrorException e) {
            throw e; // 4xx: o AirlinesHub recusou, não houve venda
        } catch (ResourceAccessException e) {
            if (neverConnected(e)) {
                throw e; // a requisição nem saiu, não houve venda
            }
            throw new SaleUnknownException(hasCause(e, HttpTimeoutException.class)
                    ? "timeout" : "conexao-interrompida", e);
        } catch (HttpServerErrorException e) {
            throw new SaleUnknownException("status-" + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new SaleUnknownException("resposta-ilegivel", e);
        }

        SellResponse body = response.getBody();
        if (response.getStatusCode().value() != 201 || body == null
                || body.transactionId() == null || body.transactionId().isBlank()) {
            throw new SaleUnknownException("resposta-sem-confirmacao", null);
        }
        return body;
    }

    private static boolean neverConnected(Throwable t) {
        return hasCause(t, ConnectException.class) || hasCause(t, HttpConnectTimeoutException.class);
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (type.isInstance(cur)) {
                return true;
            }
        }
        return false;
    }
}
