package br.ufrn.imd.imdtravel.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import br.ufrn.imd.imdtravel.dtos.BuyTicketRequest;
import br.ufrn.imd.imdtravel.exceptions.InvalidRequestException;

class BuyTicketServiceTest {

    private final BuyTicketService service = new BuyTicketService(null, null);

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
}
