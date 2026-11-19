package br.ufrn.imd.imdtravel.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import br.ufrn.imd.imdtravel.client.AirlinesClient;
import br.ufrn.imd.imdtravel.client.ExchangeClient;
import br.ufrn.imd.imdtravel.dtos.BuyTicketRequest;
import br.ufrn.imd.imdtravel.dtos.BuyTicketResponse;
import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;
import br.ufrn.imd.imdtravel.dtos.clients.FlightResponse;
import br.ufrn.imd.imdtravel.exceptions.InvalidRequestException;

@Service
public class BuyTicketService {

    private final AirlinesClient airlines;
    private final ExchangeClient exchange;

    public BuyTicketService(AirlinesClient airlines, ExchangeClient exchange) {
        this.airlines = airlines;
        this.exchange = exchange;
    }

    public BuyTicketResponse buy(BuyTicketRequest request) {
        validate(request);

        FlightResponse flight = airlines.getFlight(request.flight(), request.day());   // 1
        ConvertResponse conv = exchange.convert();                                     // 2
        BigDecimal valueUSD = flight.priceUSD();
        BigDecimal valueBRL = brl(valueUSD, conv.rate());
        int bonus = bonus(valueUSD);

        // TODO tarefa #5: POST /sell (3), POST /bonus (4), gravar em SalesRepository e
        // montar o BuyTicketResponse (valueUSD, valueBRL, conv.rate(), conv.asOf(), bonus).
        throw new UnsupportedOperationException(
                "passos 3 e 4 pendentes (#5): valueBRL=" + valueBRL + " bonus=" + bonus);
    }

    /** valueBRL = priceUSD x rate, com 2 casas. */
    static BigDecimal brl(BigDecimal priceUSD, BigDecimal rate) {
        return priceUSD.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /** bonus = parte inteira do preço em dólar. */
    static int bonus(BigDecimal priceUSD) {
        return priceUSD.setScale(0, RoundingMode.DOWN).intValueExact();
    }

    private void validate(BuyTicketRequest r) {
        if (r == null || blank(r.flight()) || blank(r.day()) || blank(r.user())) {
            throw new InvalidRequestException("flight, day e user são obrigatórios");
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
