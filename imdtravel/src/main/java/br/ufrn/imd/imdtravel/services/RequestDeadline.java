package br.ufrn.imd.imdtravel.services;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import br.ufrn.imd.imdtravel.exceptions.DeadlineExceededException;

/**
 * Controla o orçamento total de tempo (deadline) de uma operação de compra.
 */
public final class RequestDeadline {

    private final long deadlineMs;
    private final long startNanos;
    private final LongSupplier clock;

    private RequestDeadline(long deadlineMs, LongSupplier clock) {
        this.deadlineMs = deadlineMs;
        this.clock = clock;
        this.startNanos = clock.getAsLong();
    }

    public static RequestDeadline start(long deadlineMs) {
        return new RequestDeadline(deadlineMs, System::nanoTime);
    }

    public static RequestDeadline start(long deadlineMs, LongSupplier clock) {
        return new RequestDeadline(deadlineMs, clock);
    }

    public long elapsedMs() {
        long elapsedNanos = Math.max(0L, clock.getAsLong() - startNanos);
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
    }

    public long remainingMs() {
        return deadlineMs - elapsedMs();
    }

    public void checkRemaining(String stage) {
        long elapsed = elapsedMs();
        if (elapsed >= deadlineMs) {
            throw new DeadlineExceededException(stage, elapsed);
        }
    }
}
