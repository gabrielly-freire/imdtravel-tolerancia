package br.ufrn.imd.imdtravel.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.ufrn.imd.imdtravel.client.AirlinesClient;
import br.ufrn.imd.imdtravel.client.ExchangeClient;
import br.ufrn.imd.imdtravel.client.FidelityClient;
import br.ufrn.imd.imdtravel.config.JsonLogger;
import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.dtos.BuyTicketRequest;
import br.ufrn.imd.imdtravel.dtos.BuyTicketResponse;
import br.ufrn.imd.imdtravel.dtos.clients.BonusResponse;
import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;
import br.ufrn.imd.imdtravel.dtos.clients.FlightResponse;
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.DeadlineExceededException;
import br.ufrn.imd.imdtravel.exceptions.InvalidRateException;
import br.ufrn.imd.imdtravel.exceptions.InvalidRequestException;
import br.ufrn.imd.imdtravel.exceptions.NotFoundException;
import br.ufrn.imd.imdtravel.exceptions.SaleUnknownException;
import br.ufrn.imd.imdtravel.exceptions.SoldOutException;
import br.ufrn.imd.imdtravel.models.Sale;
import br.ufrn.imd.imdtravel.repositories.SalesRepository;

class BuyTicketServiceTest {

    private AirlinesClient airlines;
    private ExchangeClient exchange;
    private FidelityClient fidelity;
    private SalesRepository sales;
    private BuyTicketService service;

    @BeforeEach
    void setUp() {
        airlines = mock(AirlinesClient.class);
        exchange = mock(ExchangeClient.class);
        fidelity = mock(FidelityClient.class);
        sales = mock(SalesRepository.class);
        service = new BuyTicketService(airlines, exchange, fidelity, sales,
                ResilienceProperties.defaults(), new JsonLogger("imdtravel"), new QuoteCache(600000L, 60000L));
    }

    private void stubHappyPath() {
        when(airlines.getFlight("IMD100", "2026-12-01"))
                .thenReturn(new FlightResponse("IMD100", "2026-12-01", new BigDecimal("350.50"), 20));
        when(exchange.convert()).thenReturn(new ConvertResponse(new BigDecimal("5.50"), "2026-11-22T10:00:00Z"));
        when(airlines.sell("IMD100", "2026-12-01")).thenReturn(new SellResponse("tx-1", 19));
        when(fidelity.bonus("ana", 350)).thenReturn(new BonusResponse("ana", 350));
    }

    @Test
    void shouldComputeBrlWithTwoDecimals() {
        assertEquals(new BigDecimal("1927.75"), BuyTicketService.brl(new BigDecimal("350.50"), new BigDecimal("5.50")));
    }

    @Test
    void shouldUseIntegerPartOfPriceAsBonus() {
        assertEquals(350, BuyTicketService.bonus(new BigDecimal("350.99")));
    }

    @Test
    void shouldRejectMissingOrBlankFields() {
        assertThrows(InvalidRequestException.class, () -> service.buy(new BuyTicketRequest("IMD100", null, "ana")));
        assertThrows(InvalidRequestException.class, () -> service.buy(new BuyTicketRequest("IMD100", "2026-12-01", " ")));
        assertThrows(InvalidRequestException.class, () -> service.buy(null));
    }

    @Test
    void shouldReturnCompletedPurchaseAndRecordSaleWhenAllDependenciesSucceed() {
        stubHappyPath();

        BuyTicketResponse r = service.buy(new BuyTicketRequest("IMD100", "2026-12-01", "ana"));

        assertEquals("tx-1", r.transactionId());
        assertEquals(new BigDecimal("1927.75"), r.valueBRL());
        assertEquals(350, r.bonus());
        assertEquals("2026-11-22T10:00:00Z", r.rateAsOf());
        assertFalse(r.degraded());
        assertEquals(List.of(), r.degradations());
        verify(sales).save(any(Sale.class));
    }

    private static final BuyTicketRequest ORDER = new BuyTicketRequest("IMD100", "2026-12-01", "ana");

    @Test
    void shouldUseCachedPriceAndRateWhenBothDependenciesFail() {
        stubHappyPath();
        service.buy(ORDER);
        when(airlines.getFlight(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        when(exchange.convert()).thenThrow(new InvalidRateException("fora da faixa", null));

        BuyTicketResponse r = service.buy(ORDER);

        assertTrue(r.degraded());
        assertEquals(List.of("price-cached", "rate-cached"), r.degradations());
        assertEquals(new BigDecimal("1927.75"), r.valueBRL());
        assertEquals("2026-11-22T10:00:00Z", r.rateAsOf());
        verify(airlines, times(2)).sell("IMD100", "2026-12-01");
    }

    @Test
    void shouldFailBeforeSellingWhenThereIsNoCachedData() {
        stubHappyPath();
        when(exchange.convert()).thenThrow(new InvalidRateException("fora da faixa", null));

        assertThrows(InvalidRateException.class, () -> service.buy(ORDER));
        verify(airlines, never()).sell(anyString(), anyString());
        verify(sales, never()).save(any());
    }

    @Test
    void shouldNotUsePriceCachedForAnotherDay() {
        stubHappyPath();
        service.buy(ORDER);
        when(airlines.getFlight("IMD100", "2026-12-02")).thenThrow(new RuntimeException("timeout"));

        assertThrows(RuntimeException.class,
                () -> service.buy(new BuyTicketRequest("IMD100", "2026-12-02", "ana")));
        verify(airlines, never()).sell("IMD100", "2026-12-02");
    }

    @Test
    void shouldNotReplaceNotFoundWithCachedPrice() {
        stubHappyPath();
        service.buy(ORDER);
        when(airlines.getFlight(anyString(), anyString())).thenThrow(new NotFoundException("inexistente"));

        assertThrows(NotFoundException.class, () -> service.buy(ORDER));
        verify(airlines, times(1)).sell(anyString(), anyString());
    }

    @Test
    void shouldNotChargeBonusNorRecordSaleWhenSoldOut() {
        stubHappyPath();
        when(airlines.sell(anyString(), anyString())).thenThrow(new SoldOutException("sold-out"));

        assertThrows(SoldOutException.class, () -> service.buy(new BuyTicketRequest("IMD100", "2026-12-01", "ana")));
        verify(fidelity, never()).bonus(anyString(), anyInt());
        verify(sales, never()).save(any());
    }

    @Test
    void shouldRecordSaleAndDeclareDegradationWhenFidelityFails() {
        stubHappyPath();
        when(fidelity.bonus(anyString(), anyInt())).thenThrow(new RuntimeException("fidelity down"));

        BuyTicketResponse r = service.buy(new BuyTicketRequest("IMD100", "2026-12-01", "ana"));

        assertEquals("tx-1", r.transactionId());
        assertTrue(r.degraded());
        assertEquals(List.of("bonus-not-credited"), r.degradations());
        verify(sales).save(any(Sale.class));
    }

    @Test
    void shouldSellOnceAndRecordNothingWhenSaleResultIsUnknown() {
        stubHappyPath();
        when(airlines.sell(anyString(), anyString())).thenThrow(new SaleUnknownException("timeout", null));

        assertThrows(SaleUnknownException.class,
                () -> service.buy(new BuyTicketRequest("IMD100", "2026-12-01", "ana")));
        verify(airlines, times(1)).sell(anyString(), anyString());
        verify(fidelity, never()).bonus(anyString(), anyInt());
        verify(sales, never()).save(any());
    }

    @Test
    void shouldCompleteSaleWithoutBonusWhenDeadlineExpiresDuringSell() {
        ResilienceProperties tightDeadline = new ResilienceProperties(1000L, 500L, 2000L, 500L, 200L, 50, 4, 2, 5000L, 1);
        BuyTicketService tightService = new BuyTicketService(
                airlines, exchange, fidelity, sales, tightDeadline, new JsonLogger("imdtravel"),
                new QuoteCache(600000L, 60000L));
        stubHappyPath();
        when(airlines.sell("IMD100", "2026-12-01")).thenAnswer(inv -> {
            Thread.sleep(250L);
            return new SellResponse("tx-1", 19);
        });

        BuyTicketResponse r = tightService.buy(new BuyTicketRequest("IMD100", "2026-12-01", "ana"));

        assertEquals(List.of("bonus-not-credited"), r.degradations());
        verify(fidelity, never()).bonus(anyString(), anyInt());
        verify(sales).save(any(Sale.class));
    }

    @Test
    void shouldStopBeforeCallingNextStageWhenDeadlineExceeded() {
        ResilienceProperties tightDeadline = new ResilienceProperties(1000L, 500L, 2000L, 500L, 40L, 50, 4, 2, 5000L, 1);
        BuyTicketService tightService = new BuyTicketService(
                airlines, exchange, fidelity, sales, tightDeadline, new JsonLogger("imdtravel"),
                new QuoteCache(600000L, 60000L));

        when(airlines.getFlight("IMD100", "2026-12-01")).thenAnswer(inv -> {
            Thread.sleep(60L);
            return new FlightResponse("IMD100", "2026-12-01", new BigDecimal("350.50"), 20);
        });

        assertThrows(DeadlineExceededException.class,
                () -> tightService.buy(new BuyTicketRequest("IMD100", "2026-12-01", "ana")));
        verify(exchange, never()).convert();
        verify(airlines, never()).sell(anyString(), anyString());
        verify(sales, never()).save(any());
    }
}
