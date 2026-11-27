package br.ufrn.imd.imdtravel.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.DeadlineExceededException;
import br.ufrn.imd.imdtravel.exceptions.InvalidRequestException;
import br.ufrn.imd.imdtravel.exceptions.NotFoundException;
import br.ufrn.imd.imdtravel.exceptions.SaleUnknownException;
import br.ufrn.imd.imdtravel.models.Sale;
import br.ufrn.imd.imdtravel.repositories.SalesRepository;

@Service
public class BuyTicketService {

    /** Valor de {@code degradations}: venda confirmada, mas sem confirmação do crédito do bônus. */
    static final String BONUS_NOT_CREDITED = "bonus-not-credited";
    /** Preço vindo do cache porque o /flight falhou. */
    static final String PRICE_CACHED = "price-cached";
    /** Taxa vinda do cache porque o /convert falhou ou devolveu taxa inválida. */
    static final String RATE_CACHED = "rate-cached";

    private final AirlinesClient airlines;
    private final ExchangeClient exchange;
    private final FidelityClient fidelity;
    private final SalesRepository sales;
    private final ResilienceProperties resilienceProperties;
    private final JsonLogger logger;
    private final QuoteCache cache;

    public BuyTicketService(AirlinesClient airlines, ExchangeClient exchange,
            FidelityClient fidelity, SalesRepository sales,
            ResilienceProperties resilienceProperties, JsonLogger logger, QuoteCache cache) {
        this.airlines = airlines;
        this.exchange = exchange;
        this.fidelity = fidelity;
        this.sales = sales;
        this.resilienceProperties = resilienceProperties;
        this.logger = logger;
        this.cache = cache;
    }

    public BuyTicketResponse buy(BuyTicketRequest request) {
        validate(request);
        RequestDeadline deadline = RequestDeadline.start(resilienceProperties.deadlineTotalMs());

        List<String> degradations = new ArrayList<>();

        checkDeadline(deadline, "flight");
        BigDecimal valueUSD = price(request, degradations);                            // 1

        checkDeadline(deadline, "convert");
        ConvertResponse conv = rate(degradations);                                     // 2
        BigDecimal valueBRL = brl(valueUSD, conv.rate());
        int bonus = bonus(valueUSD);

        checkDeadline(deadline, "sell");
        SellResponse sale = sell(request);                                             // 3

        // A venda está confirmada: é gravada antes do bônus, e a falha dele não vira erro.
        sales.save(new Sale(CorrelationContext.getCid(), sale.transactionId(), request.flight(),
                request.day(), request.user(), valueUSD, valueBRL, Instant.now()));

        creditBonus(deadline, sale.transactionId(), request.user(), bonus, degradations); // 4

        return new BuyTicketResponse(sale.transactionId(), request.flight(), request.day(),
                request.user(), valueUSD, valueBRL, conv.rate(), conv.asOf(), bonus,
                !degradations.isEmpty(), List.copyOf(degradations));
    }

    /** Preço do /flight; se a chamada falhar, o último preço deste (voo, dia) ainda dentro da idade. */
    private BigDecimal price(BuyTicketRequest request, List<String> degradations) {
        try {
            BigDecimal priceUSD = airlines.getFlight(request.flight(), request.day()).priceUSD();
            cache.putPrice(request.flight(), request.day(), priceUSD);
            return priceUSD;
        } catch (NotFoundException e) {
            throw e; // resposta válida: o voo ou dia não existe
        } catch (RuntimeException e) {
            return cached(cache.price(request.flight(), request.day()), "preco", PRICE_CACHED, degradations, e);
        }
    }

    /** Taxa válida do /convert; se não houver, a última taxa válida ainda dentro da idade. */
    private ConvertResponse rate(List<String> degradations) {
        try {
            ConvertResponse conv = exchange.convert();
            cache.putRate(conv);
            return conv;
        } catch (RuntimeException e) {
            return cached(cache.rate(), "taxa", RATE_CACHED, degradations, e);
        }
    }

    /** Sem dado dentro da idade, a falha original sobe e a compra para antes do /sell. */
    private <T> T cached(Optional<QuoteCache.Cached<T>> entry, String data, String degradation,
            List<String> degradations, RuntimeException failure) {
        String reason = failure.getClass().getSimpleName();
        if (entry.isEmpty()) {
            logger.logCacheMiss(CorrelationContext.getCid(), data, reason);
            throw failure;
        }
        logger.logCacheUsed(CorrelationContext.getCid(), data, entry.get().ageMs(), reason);
        degradations.add(degradation);
        return entry.get().value();
    }

    private SellResponse sell(BuyTicketRequest request) {
        try {
            return airlines.sell(request.flight(), request.day());
        } catch (SaleUnknownException e) {
            logger.logSaleUnknown(CorrelationContext.getCid(), request.flight(), request.day(),
                    request.user(), e.getReason());
            throw e;
        }
    }

    private void creditBonus(RequestDeadline deadline, String transactionId, String user, int bonus,
            List<String> degradations) {
        try {
            checkDeadline(deadline, "bonus");
            fidelity.bonus(user, bonus);
        } catch (RuntimeException e) {
            logger.logBonusNotCredited(CorrelationContext.getCid(), transactionId, user, bonus,
                    e.getClass().getSimpleName());
            degradations.add(BONUS_NOT_CREDITED);
        }
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
