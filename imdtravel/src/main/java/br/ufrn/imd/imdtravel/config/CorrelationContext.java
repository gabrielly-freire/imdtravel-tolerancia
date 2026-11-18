package br.ufrn.imd.imdtravel.config;

import java.util.UUID;
import org.slf4j.MDC;

public final class CorrelationContext {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "cid";

    private CorrelationContext() {
    }

    public static String resolveOrGenerate(String incomingCid) {
        if (incomingCid != null && !incomingCid.isBlank()) {
            return incomingCid.trim();
        }
        return UUID.randomUUID().toString();
    }

    public static void setCid(String cid) {
        MDC.put(MDC_KEY, cid);
    }

    public static String getCid() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
