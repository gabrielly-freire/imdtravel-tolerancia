package br.ufrn.imd.imdtravel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Parâmetros de resiliência do IMDTravel configurados via variáveis de ambiente.
 */
@Component
public class ResilienceProperties {

    private final long timeoutFlightMs;
    private final long timeoutConvertMs;
    private final long timeoutSellMs;
    private final long timeoutBonusMs;
    private final long deadlineTotalMs;
    private final int cbFailureRateThreshold;
    private final int cbSlidingWindowSize;
    private final int cbMinimumNumberOfCalls;
    private final long cbWaitDurationOpenMs;
    private final int cbHalfOpenPermittedCalls;

    public ResilienceProperties(
            @Value("${imdtravel.resilience.timeout-flight-ms:1400}") long timeoutFlightMs,
            @Value("${imdtravel.resilience.timeout-convert-ms:700}") long timeoutConvertMs,
            @Value("${imdtravel.resilience.timeout-sell-ms:1600}") long timeoutSellMs,
            @Value("${imdtravel.resilience.timeout-bonus-ms:1100}") long timeoutBonusMs,
            @Value("${imdtravel.resilience.deadline-total-ms:4500}") long deadlineTotalMs,
            @Value("${imdtravel.resilience.cb-failure-rate-threshold:50}") int cbFailureRateThreshold,
            @Value("${imdtravel.resilience.cb-sliding-window-size:6}") int cbSlidingWindowSize,
            @Value("${imdtravel.resilience.cb-minimum-number-of-calls:3}") int cbMinimumNumberOfCalls,
            @Value("${imdtravel.resilience.cb-wait-duration-open-ms:5000}") long cbWaitDurationOpenMs,
            @Value("${imdtravel.resilience.cb-half-open-permitted-calls:1}") int cbHalfOpenPermittedCalls) {
        this.timeoutFlightMs = timeoutFlightMs;
        this.timeoutConvertMs = timeoutConvertMs;
        this.timeoutSellMs = timeoutSellMs;
        this.timeoutBonusMs = timeoutBonusMs;
        this.deadlineTotalMs = deadlineTotalMs;
        this.cbFailureRateThreshold = cbFailureRateThreshold;
        this.cbSlidingWindowSize = cbSlidingWindowSize;
        this.cbMinimumNumberOfCalls = cbMinimumNumberOfCalls;
        this.cbWaitDurationOpenMs = cbWaitDurationOpenMs;
        this.cbHalfOpenPermittedCalls = cbHalfOpenPermittedCalls;
    }

    public static ResilienceProperties defaults() {
        return new ResilienceProperties(1400L, 700L, 1600L, 1100L, 4500L, 50, 6, 3, 5000L, 1);
    }

    public long timeoutFlightMs() {
        return timeoutFlightMs;
    }

    public long timeoutConvertMs() {
        return timeoutConvertMs;
    }

    public long timeoutSellMs() {
        return timeoutSellMs;
    }

    public long timeoutBonusMs() {
        return timeoutBonusMs;
    }

    public long deadlineTotalMs() {
        return deadlineTotalMs;
    }

    public int cbFailureRateThreshold() {
        return cbFailureRateThreshold;
    }

    public int cbSlidingWindowSize() {
        return cbSlidingWindowSize;
    }

    public int cbMinimumNumberOfCalls() {
        return cbMinimumNumberOfCalls;
    }

    public long cbWaitDurationOpenMs() {
        return cbWaitDurationOpenMs;
    }

    public int cbHalfOpenPermittedCalls() {
        return cbHalfOpenPermittedCalls;
    }
}
