package br.ufrn.imd.imdtravel.dtos.clients;

/** Resposta 201 de POST /sell. */
public record SellResponse(String transactionId, int seatsRemaining) {
}
