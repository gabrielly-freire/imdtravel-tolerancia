package br.ufrn.imd.imdtravel.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.ufrn.imd.imdtravel.dtos.clients.FlightResponse;
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.NotFoundException;

@Component
public class AirlinesHttpClient implements AirlinesClient {

    private final RestClient http;

    public AirlinesHttpClient(RestClient.Builder builder,
            @Value("${imdtravel.airlineshub-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @Override
    public FlightResponse getFlight(String flight, String day) {
        return http.get()
                .uri(u -> u.path("/flight").queryParam("flight", flight).queryParam("day", day).build())
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new NotFoundException("voo ou dia inexistente");
                })
                .body(FlightResponse.class);
    }

    @Override
    public SellResponse sell(String flight, String day) {
        throw new UnsupportedOperationException("POST /sell: tarefa #5");
    }
}
