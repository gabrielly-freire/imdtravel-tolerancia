package br.ufrn.imd.imdtravel.client;

import br.ufrn.imd.imdtravel.dtos.clients.BonusResponse;

/** Chamada 4 ao Fidelity. */
public interface FidelityClient {

    /** POST /bonus { user, bonus } */
    BonusResponse bonus(String user, int bonus);
}
