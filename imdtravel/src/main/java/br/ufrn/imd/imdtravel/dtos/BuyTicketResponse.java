package br.ufrn.imd.imdtravel.dtos;

import java.math.BigDecimal;

/** Resposta 201 de POST /buyTicket (os 9 campos que o testes.sh confere). */
public record BuyTicketResponse(
        String transactionId,
        String flight,
        String day,
        String user,
        BigDecimal valueUSD,
        BigDecimal valueBRL,
        BigDecimal rate,
        String rateAsOf,
        int bonus) {
}
