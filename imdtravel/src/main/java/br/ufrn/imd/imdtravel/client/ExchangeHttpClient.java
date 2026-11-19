package br.ufrn.imd.imdtravel.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;

@Component
public class ExchangeHttpClient implements ExchangeClient {

    private final RestClient http;

    public ExchangeHttpClient(RestClient.Builder builder,
            @Value("${imdtravel.exchange-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @Override
    public ConvertResponse convert() {
        return http.get().uri("/convert").retrieve().body(ConvertResponse.class);
    }
}
