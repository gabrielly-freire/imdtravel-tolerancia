package br.ufrn.imd.imdtravel.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;

class QuoteCacheTest {

    private final AtomicLong nanos = new AtomicLong();
    private final QuoteCache cache = new QuoteCache(600000L, 60000L, nanos::get);

    private void advance(long ms) {
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(ms));
    }

    @Test
    void shouldReturnRateUpToItsMaximumAgeAndNotAfter() {
        cache.putRate(new ConvertResponse(new BigDecimal("5.50"), "2026-11-26T10:00:00Z"));

        advance(60000L);
        assertEquals(60000L, cache.rate().orElseThrow().ageMs());
        advance(1L);
        assertTrue(cache.rate().isEmpty());
    }

    @Test
    void shouldKeepPricePerFlightAndDayUpToItsMaximumAge() {
        cache.putPrice("IMD100", "2026-12-01", new BigDecimal("350.50"));

        assertTrue(cache.price("IMD100", "2026-12-02").isEmpty());
        advance(600000L);
        assertEquals(new BigDecimal("350.50"), cache.price("IMD100", "2026-12-01").orElseThrow().value());
        advance(1L);
        assertTrue(cache.price("IMD100", "2026-12-01").isEmpty());
    }
}
