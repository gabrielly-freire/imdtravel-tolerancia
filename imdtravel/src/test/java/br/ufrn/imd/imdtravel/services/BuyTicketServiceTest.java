package br.ufrn.imd.imdtravel.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.ufrn.imd.imdtravel.client.AirlinesClient;
import br.ufrn.imd.imdtravel.client.ExchangeClient;
import br.ufrn.imd.imdtravel.client.FidelityClient;
import br.ufrn.imd.imdtravel.dtos.BuyTicketRequest;
import br.ufrn.imd.imdtravel.dtos.BuyTicketResponse;
import br.ufrn.imd.imdtravel.dtos.clients.BonusResponse;
import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;
import br.ufrn.imd.imdtravel.dtos.clients.FlightResponse;
import br.ufrn.imd.imdtravel.dtos.clients.SellResponse;
import br.ufrn.imd.imdtravel.exceptions.InvalidRequestException;
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
        service = new BuyTicketService(airlines, exchange, fidelity, sales);
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
        verify(sales).save(any(Sale.class));
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
    void shouldPropagateFailureAndNotRecordSaleWhenFidelityFails() {
        stubHappyPath();
        when(fidelity.bonus(anyString(), anyInt())).thenThrow(new RuntimeException("fidelity down"));

        assertThrows(RuntimeException.class, () -> service.buy(new BuyTicketRequest("IMD100", "2026-12-01", "ana")));
        verify(sales, never()).save(any());
    }
}
