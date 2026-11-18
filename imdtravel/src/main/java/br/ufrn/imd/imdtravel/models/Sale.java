package br.ufrn.imd.imdtravel.models;

import java.math.BigDecimal;
import java.time.Instant;

/** Uma linha do registro de vendas (GET /sales). Os nomes dos campos são os que o testes.sh confere. */
public record Sale(
        String correlationId,
        String transactionId,
        String flight,
        String day,
        String user,
        BigDecimal valueUSD,
        BigDecimal valueBRL,
        Instant ts) {
}
