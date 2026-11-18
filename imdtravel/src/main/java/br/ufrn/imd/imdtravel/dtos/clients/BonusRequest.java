package br.ufrn.imd.imdtravel.dtos.clients;

/** Corpo de POST /bonus. */
public record BonusRequest(String user, int bonus) {
}
