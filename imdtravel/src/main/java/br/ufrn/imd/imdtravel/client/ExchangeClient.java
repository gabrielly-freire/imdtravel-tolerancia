package br.ufrn.imd.imdtravel.client;

import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;

/** Chamada 2 ao Exchange. */
public interface ExchangeClient {

    /** GET /convert */
    ConvertResponse convert();
}
