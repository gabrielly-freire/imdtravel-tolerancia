package br.ufrn.imd.imdtravel.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpServer;

import br.ufrn.imd.imdtravel.config.JsonLogger;
import br.ufrn.imd.imdtravel.config.ResilienceConfig;
import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.config.RestClientConfig;
import br.ufrn.imd.imdtravel.exceptions.InvalidRateException;

class ExchangeHttpClientTest {

    private HttpServer server;
    private ExchangeHttpClient client;
    private volatile String body;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/convert", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        ResilienceProperties props = ResilienceProperties.defaults();
        client = new ExchangeHttpClient(new RestClientConfig(), props,
                new ResilienceConfig().circuitBreakerRegistry(props, new JsonLogger("imdtravel")),
                "http://" + server.getAddress().getAddress().getHostAddress() + ":" + server.getAddress().getPort(),
                new BigDecimal("5.0"), new BigDecimal("6.0"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5.0", "5.5", "6.0"})
    void shouldAcceptRateInsideTheRange(String rate) {
        body = "{\"rate\":" + rate + ",\"asOf\":\"2026-11-26T10:00:00Z\"}";

        assertEquals(0, new BigDecimal(rate).compareTo(client.convert().rate()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.5", "4.99", "6.01", "5500000000", "\"5,50\"", "\"NaN\"", "\"abc\"",
            "\"\"", "null", "NaN"})
    void shouldRejectInvalidRate(String rate) {
        body = "{\"rate\":" + rate + ",\"asOf\":\"2026-11-26T10:00:00Z\"}";

        assertThrows(InvalidRateException.class, client::convert);
    }

    @Test
    void shouldRejectBodyWithoutRate() {
        body = "{}";

        assertThrows(InvalidRateException.class, client::convert);
    }
}
