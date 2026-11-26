package br.ufrn.imd.imdtravel.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import br.ufrn.imd.imdtravel.config.JsonLogger;
import br.ufrn.imd.imdtravel.config.ResilienceConfig;
import br.ufrn.imd.imdtravel.config.ResilienceProperties;
import br.ufrn.imd.imdtravel.controllers.StatusController;
import br.ufrn.imd.imdtravel.exceptions.DeadlineExceededException;
import br.ufrn.imd.imdtravel.exceptions.NotFoundException;
import br.ufrn.imd.imdtravel.exceptions.SoldOutException;
import br.ufrn.imd.imdtravel.services.RequestDeadline;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

class ResilienceTest {

    private CircuitBreakerRegistry createRegistry(long waitOpenMs) {
        ResilienceProperties props = new ResilienceProperties(
                1000L, 500L, 2000L, 500L, 4500L,
                50, 4, 2, waitOpenMs, 1);
        return new ResilienceConfig().circuitBreakerRegistry(props, new JsonLogger("imdtravel"));
    }

    @Test
    void circuitBreakerOpensAtThresholdRejectsFastAndRecoversAutomatically() throws InterruptedException {
        CircuitBreakerRegistry registry = createRegistry(120L);
        CircuitBreaker cb = registry.circuitBreaker("flight");
        AtomicInteger attempts = new AtomicInteger(0);

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class, () -> cb.executeSupplier(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("timeout");
            }));
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertEquals(2, attempts.get());

        // Em OPEN, deve falhar rápido sem invocar a dependência
        assertThrows(CallNotPermittedException.class, () -> cb.executeSupplier(() -> {
            attempts.incrementAndGet();
            return "ok";
        }));
        assertEquals(2, attempts.get());

        // Após o tempo de espera em OPEN, transiciona para HALF_OPEN e fecha no sucesso
        Thread.sleep(180L);
        String result = cb.executeSupplier(() -> {
            attempts.incrementAndGet();
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(3, attempts.get());
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void businessExceptionsDoNotOpenCircuitBreaker() {
        CircuitBreakerRegistry registry = createRegistry(5000L);
        CircuitBreaker flightCb = registry.circuitBreaker("flight");
        CircuitBreaker sellCb = registry.circuitBreaker("sell");

        for (int i = 0; i < 6; i++) {
            assertThrows(NotFoundException.class, () -> flightCb.executeSupplier(() -> {
                throw new NotFoundException("voo inexistente");
            }));
            assertThrows(SoldOutException.class, () -> sellCb.executeSupplier(() -> {
                throw new SoldOutException("sold-out");
            }));
        }

        assertEquals(CircuitBreaker.State.CLOSED, flightCb.getState());
        assertEquals(CircuitBreaker.State.CLOSED, sellCb.getState());
    }

    @Test
    void requestDeadlineTracksElapsedAndThrowsWhenExpired() {
        AtomicLong nowNanos = new AtomicLong(1_000_000_000L);
        RequestDeadline deadline = RequestDeadline.start(4500L, nowNanos::get);

        assertEquals(0L, deadline.elapsedMs());
        assertEquals(4500L, deadline.remainingMs());
        deadline.checkRemaining("flight");

        nowNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(2000L));
        assertEquals(2000L, deadline.elapsedMs());
        assertEquals(2500L, deadline.remainingMs());
        deadline.checkRemaining("sell");

        nowNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(2500L));
        DeadlineExceededException ex = assertThrows(DeadlineExceededException.class,
                () -> deadline.checkRemaining("bonus"));
        assertEquals("bonus", ex.getStage());
        assertEquals(4500L, ex.getElapsedMs());
    }

    @Test
    void statusControllerReflectsHealthyAndDegradedStates() {
        ResilienceProperties props = ResilienceProperties.defaults();
        CircuitBreakerRegistry registry = new ResilienceConfig().circuitBreakerRegistry(props, new JsonLogger("imdtravel"));
        StatusController controller = new StatusController(registry, props);

        Map<String, Object> initial = controller.getStatus();
        assertEquals("ok", initial.get("status"));

        CircuitBreaker convertCb = registry.circuitBreaker("convert");
        convertCb.transitionToOpenState();

        Map<String, Object> degraded = controller.getStatus();
        assertEquals("degraded", degraded.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> circuits = (Map<String, Map<String, Object>>) degraded.get("circuits");
        assertEquals("OPEN", circuits.get("convert").get("state"));
        assertEquals("CLOSED", circuits.get("flight").get("state"));
    }
}
