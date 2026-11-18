package br.ufrn.imd.imdtravel.dtos.config;

/** Corpo dos erros: { "error": "not-found" | "sold-out" | "invalid-request" }. */
public record ErrorResponse(String error) {
}
