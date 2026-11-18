package br.ufrn.imd.imdtravel.dtos.clients;

import java.math.BigDecimal;

/** Resposta de GET /convert. asOf fica como String para ser devolvido sem alteração em rateAsOf. */
public record ConvertResponse(BigDecimal rate, String asOf) {
}
