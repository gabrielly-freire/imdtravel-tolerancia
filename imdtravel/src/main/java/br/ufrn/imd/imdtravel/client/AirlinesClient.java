package br.ufrn.imd.imdtravel.client;

import br.ufrn.imd.imdtravel.dtos.clients.FlightResponse;
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.NotFoundException;
import br.ufrn.imd.imdtravel.exceptions.SoldOutException;

/** Chamadas 1 e 3 ao AirlinesHub. */
public interface AirlinesClient {

    /**
     * GET /flight?flight=&day=
     *
     * @throws NotFoundException quando o AirlinesHub devolve 404 (voo ou dia inexistente)
     */
    FlightResponse getFlight(String flight, String day);

    /**
     * POST /sell { flight, day } — consome um assento.
     *
     * @throws SoldOutException quando o AirlinesHub devolve 409 (sem assento)
     */
    SellResponse sell(String flight, String day);
}
