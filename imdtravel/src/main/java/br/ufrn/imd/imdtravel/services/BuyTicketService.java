package br.ufrn.imd.imdtravel.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.stereotype.Service;

import br.ufrn.imd.imdtravel.client.AirlinesClient;
import br.ufrn.imd.imdtravel.client.ExchangeClient;
import br.ufrn.imd.imdtravel.client.FidelityClient;
import br.ufrn.imd.imdtravel.config.CorrelationContext;
import br.ufrn.imd.imdtravel.config.JsonLogger;
import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.dtos.BuyTicketRequest;
import br.ufrn.imd.imdtravel.dtos.BuyTicketResponse;
import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;
import br.ufrn.imd.imdtravel.dtos.clients.FlightResponse;
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.DeadlineExceededException;
import br.ufrn.imd.imdtravel.exceptions.InvalidRequestException;
import br.ufrn.imd.imdtravel.models.Sale;
import br.ufrn.imd.imdtravel.repositories.SalesRepository;

@Service
public class BuyTicketService {

    private final AirlinesClient airlines;
    private final ExchangeClient exchange;
    private final FidelityClient fidelity;
    private final SalesRepository sales;
    private final ResilienceProperties resilienceProperties;
    private final JsonLogger logger;

    public BuyTicketService(AirlinesClient airlines, ExchangeClient exchange,
            FidelityClient fidelity, SalesRepository sales,
            ResilienceProperties resilienceProperties, JsonLogger logger) {
        this.airlines = airlines;
        this.exchange = exchange;
        this.fidelity = fidelity;
        this.sales = sales;
        this.resilienceProperties = resilienceProperties;
        this.logger = logger;
    }

    public BuyTicketResponse buy(BuyTicketRequest request) {
        validate(request);
        RequestDeadline deadline = RequestDeadline.start(resilienceProperties.deadlineTotalMs());

        checkDeadline(deadline, "flight");
        FlightResponse flight = airlines.getFlight(request.flight(), request.day());   // 1

        checkDeadline(deadline, "convert");
        ConvertResponse conv = exchange.convert();                                     // 2
        BigDecimal valueUSD = flight.priceUSD();
        BigDecimal valueBRL = brl(valueUSD, conv.rate());
        int bonus = bonus(valueUSD);

        checkDeadline(deadline, "sell");
        SellResponse sale = airlines.sell(request.flight(), request.day());           // 3

        checkDeadline(deadline, "bonus");
        fidelity.bonus(request.user(), bonus);                                         // 4

        sales.save(new Sale(CorrelationContext.getCid(), sale.transactionId(), request.flight(),
                request.day(), request.user(), valueUSD, valueBRL, Instant.now()));

        return new BuyTicketResponse(sale.transactionId(), request.flight(), request.day(),
                request.user(), valueUSD, valueBRL, conv.rate(), conv.asOf(), bonus);
    }

    private void checkDeadline(RequestDeadline deadline, String stage) {
        try {
            deadline.checkRemaining(stage);
        } catch (DeadlineExceededException e) {
            logger.logDeadlineExceeded(CorrelationContext.getCid(), stage, e.getElapsedMs(),
                    resilienceProperties.deadlineTotalMs());
            throw e;
        }
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
