package br.ufrn.imd.imdtravel.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.ufrn.imd.imdtravel.dtos.clients.BonusRequest;
import br.ufrn.imd.imdtravel.dtos.clients.BonusResponse;

@Component
public class FidelityHttpClient implements FidelityClient {

    private final RestClient http;

    public FidelityHttpClient(RestClient.Builder builder,
            @Value("${imdtravel.fidelity-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @Override
    public BonusResponse bonus(String user, int bonus) {
        return http.post()
                .uri("/bonus")
                .body(new BonusRequest(user, bonus))
                .retrieve()
                .body(BonusResponse.class);
    }
}
