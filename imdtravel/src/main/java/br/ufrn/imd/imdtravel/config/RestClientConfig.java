package br.ufrn.imd.imdtravel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            String cid = CorrelationContext.getCid();
            if (cid != null && !cid.isBlank()
                    && !request.getHeaders().containsHeader(CorrelationContext.HEADER_NAME)) {
                request.getHeaders().set(CorrelationContext.HEADER_NAME, cid);
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder(ClientHttpRequestInterceptor correlationIdInterceptor) {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .requestInterceptor(correlationIdInterceptor);
    }
}
