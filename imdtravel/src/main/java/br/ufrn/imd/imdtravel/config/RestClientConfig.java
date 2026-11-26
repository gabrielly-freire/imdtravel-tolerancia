package br.ufrn.imd.imdtravel.config;

import java.net.http.HttpClient;
import java.time.Duration;

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

    public RestClient createClient(String baseUrl, long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(correlationIdInterceptor())
                .build();
    }
}
