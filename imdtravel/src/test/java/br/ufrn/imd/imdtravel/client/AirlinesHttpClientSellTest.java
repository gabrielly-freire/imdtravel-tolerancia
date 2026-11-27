package br.ufrn.imd.imdtravel.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import br.ufrn.imd.imdtravel.config.JsonLogger;
import br.ufrn.imd.imdtravel.config.ResilienceConfig;
import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.config.RestClientConfig;
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.SaleUnknownException;
import br.ufrn.imd.imdtravel.exceptions.SoldOutException;

/** POST /sell contra um servidor falso em socket puro, que conta quantas vezes a venda chegou. */
class AirlinesHttpClientSellTest {

    private static final long SELL_TIMEOUT_MS = 2000L;

    private final AtomicInteger requests = new AtomicInteger();
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private ServerSocket server;

    @AfterEach
    void tearDown() throws IOException {
        for (Socket s : sockets) {
            s.close();
        }
        if (server != null) {
            server.close();
        }
    }

    /** {@code reply} nulo: não responde nada e, se {@code close}, fecha a conexão. */
    private AirlinesHttpClient clientFor(String reply, boolean close) throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    sockets.add(socket);
                    socket.getInputStream().read(new byte[4096]);
                    requests.incrementAndGet();
                    if (reply != null) {
                        OutputStream out = socket.getOutputStream();
                        out.write(reply.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        // Só encerra a escrita: fechar com bytes da requisição ainda não lidos
                        // envia RST e o cliente pode perder a resposta. O tearDown fecha o socket.
                        socket.shutdownOutput();
                    } else if (close) {
                        socket.close();
                    }
                } catch (IOException e) {
                    return;
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
        return client("http://" + server.getInetAddress().getHostAddress() + ":" + server.getLocalPort());
    }

    private AirlinesHttpClient client(String baseUrl) {
        ResilienceProperties props = new ResilienceProperties(
                1000L, 500L, SELL_TIMEOUT_MS, 500L, 4500L, 50, 6, 3, 5000L, 1);
        return new AirlinesHttpClient(new RestClientConfig(), props,
                new ResilienceConfig().circuitBreakerRegistry(props, new JsonLogger("imdtravel")), baseUrl);
    }

    private static String http(String status, String body) {
        return "HTTP/1.1 " + status + "\r\nContent-Type: application/json\r\nContent-Length: "
                + body.length() + "\r\nConnection: close\r\n\r\n" + body;
    }

    private static String unknownReason(AirlinesHttpClient client) {
        return assertThrows(SaleUnknownException.class, () -> client.sell("IMD100", "2026-12-01")).getReason();
    }

    @Test
    void shouldReturnSaleWhenConfirmedWith201() throws IOException {
        var client = clientFor(http("201 Created", "{\"transactionId\":\"tx-1\",\"seatsRemaining\":19}"), true);

        assertEquals(new SellResponse("tx-1", 19), client.sell("IMD100", "2026-12-01"));
    }

    @Test
    void shouldReportUnknownWithoutRetryingWhenSellTimesOut() throws Exception {
        var client = clientFor(null, false);

        assertEquals("timeout", unknownReason(client));
        Thread.sleep(2 * SELL_TIMEOUT_MS);
        assertEquals(1, requests.get());
    }

    @Test
    void shouldReportUnknownWithoutRetryingWhenConnectionClosesWithoutResponse() throws Exception {
        var client = clientFor(null, true);

        assertEquals("conexao-interrompida", unknownReason(client));
        Thread.sleep(2 * SELL_TIMEOUT_MS);
        assertEquals(1, requests.get());
    }

    @Test
    void shouldReportUnknownWhenSellAnswers500() throws IOException {
        var client = clientFor(http("500 Internal Server Error", "{}"), true);

        assertEquals("status-500", unknownReason(client));
    }

    @Test
    void shouldReportUnknownWhen201HasNoTransactionId() throws IOException {
        var client = clientFor(http("201 Created", "{\"seatsRemaining\":19}"), true);

        assertEquals("resposta-sem-confirmacao", unknownReason(client));
    }

    @Test
    void shouldReportUnknownWhen201BodyIsUnreadable() throws IOException {
        var client = clientFor(http("201 Created", "{\"transactionId\":"), true);

        assertThrows(SaleUnknownException.class, () -> client.sell("IMD100", "2026-12-01"));
    }

    @Test
    void shouldKeepSoldOutAsKnownResult() throws IOException {
        var client = clientFor(http("409 Conflict", "{\"error\":\"sold-out\"}"), true);

        assertThrows(SoldOutException.class, () -> client.sell("IMD100", "2026-12-01"));
    }

    @Test
    void shouldKeepRejectionAsKnownResult() throws IOException {
        var client = clientFor(http("400 Bad Request", "{}"), true);

        assertThrows(HttpClientErrorException.class, () -> client.sell("IMD100", "2026-12-01"));
    }

    @Test
    void shouldNotReportUnknownWhenConnectionIsRefused() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            freePort = probe.getLocalPort();
        }
        var client = client("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + freePort);

        assertThrows(ResourceAccessException.class, () -> client.sell("IMD100", "2026-12-01"));
    }
}
