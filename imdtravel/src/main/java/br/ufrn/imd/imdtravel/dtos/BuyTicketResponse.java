package br.ufrn.imd.imdtravel.dtos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta 201 de POST /buyTicket: os 9 campos da Parte 1, que o testes.sh confere, mais a
 * declaração de degradação ({@code degraded} é true quando {@code degradations} não é vazia).
 */
public record BuyTicketResponse(
        String transactionId,
        String flight,
        String day,
        String user,
        BigDecimal valueUSD,
        BigDecimal valueBRL,
        BigDecimal rate,
        String rateAsOf,
        int bonus,
        boolean degraded,
        List<String> degradations) {
}
