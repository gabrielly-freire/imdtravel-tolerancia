package br.ufrn.imd.imdtravel.services;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.ufrn.imd.imdtravel.dtos.clients.ConvertResponse;

/**
 * Último preço bom de cada (voo, dia) e última taxa válida, em memória, cada um com idade máxima
 * contada do recebimento. Um dado mais velho que isso nunca é devolvido. Reiniciar esvazia o cache.
 */
@Component
public class QuoteCache {

    /** Um dado guardado e há quanto tempo foi recebido. */
    public record Cached<T>(T value, long ageMs) {
    }

    private record Entry<T>(T value, long storedNanos) {
    }

    private record FlightKey(String flight, String day) {
    }

    private final long priceMaxAgeMs;
    private final long rateMaxAgeMs;
    private final LongSupplier clock;
    private final Map<FlightKey, Entry<BigDecimal>> prices = new ConcurrentHashMap<>();
    private volatile Entry<ConvertResponse> rate;

    @Autowired
    public QuoteCache(
            @Value("${imdtravel.cache.price-max-age-ms:600000}") long priceMaxAgeMs,
            @Value("${imdtravel.cache.rate-max-age-ms:60000}") long rateMaxAgeMs) {
        this(priceMaxAgeMs, rateMaxAgeMs, System::nanoTime);
    }

    QuoteCache(long priceMaxAgeMs, long rateMaxAgeMs, LongSupplier clock) {
        this.priceMaxAgeMs = priceMaxAgeMs;
        this.rateMaxAgeMs = rateMaxAgeMs;
        this.clock = clock;
    }

    public void putPrice(String flight, String day, BigDecimal priceUSD) {
        prices.put(new FlightKey(flight, day), new Entry<>(priceUSD, clock.getAsLong()));
    }

    public Optional<Cached<BigDecimal>> price(String flight, String day) {
        return fresh(prices.get(new FlightKey(flight, day)), priceMaxAgeMs);
    }

    /** Só recebe taxas já validadas pelo {@code ExchangeClient}. */
    public void putRate(ConvertResponse valid) {
        rate = new Entry<>(valid, clock.getAsLong());
    }

    public Optional<Cached<ConvertResponse>> rate() {
        return fresh(rate, rateMaxAgeMs);
    }

    private <T> Optional<Cached<T>> fresh(Entry<T> entry, long maxAgeMs) {
        if (entry == null) {
            return Optional.empty();
        }
        long ageNanos = clock.getAsLong() - entry.storedNanos();
        if (ageNanos > TimeUnit.MILLISECONDS.toNanos(maxAgeMs)) {
            return Optional.empty();
        }
        return Optional.of(new Cached<>(entry.value(), TimeUnit.NANOSECONDS.toMillis(ageNanos)));
    }
}
