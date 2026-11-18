package br.ufrn.imd.imdtravel.dtos.clients;

/** Resposta 201 de POST /bonus. */
public record BonusResponse(String user, long totalPoints) {
}
