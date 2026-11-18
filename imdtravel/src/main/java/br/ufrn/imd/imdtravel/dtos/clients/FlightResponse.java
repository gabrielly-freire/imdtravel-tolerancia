package br.ufrn.imd.imdtravel.dtos.clients;

import java.math.BigDecimal;

/** Resposta de GET /flight. */
public record FlightResponse(String flight, String day, BigDecimal priceUSD, int seatsAvailable) {
}
