package br.ufrn.imd.imdtravel.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JsonLogger {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String serviceName;

    public JsonLogger(@Value("${spring.application.name:imdtravel}") String serviceName) {
        this.serviceName = serviceName;
    }

    public void logRequestReceived(String cid, String method, String path) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("svc", serviceName);
        entry.put("cid", cid);
        entry.put("event", "requisicao.recebida");
        entry.put("ms", 0L);
        entry.put("method", method);
        entry.put("path", path);
        writeLine(entry);
    }

    public void logResponseSent(String cid, String method, String path, int status, long ms) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("svc", serviceName);
        entry.put("cid", cid);
        entry.put("event", "resposta.enviada");
        entry.put("ms", ms);
        entry.put("method", method);
        entry.put("path", path);
        entry.put("status", status);
        writeLine(entry);
    }

    public void logCircuitBreakerTransition(String endpoint, String fromState, String toState) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("svc", serviceName);
        String cid = CorrelationContext.getCid();
        if (cid != null && !cid.isBlank()) {
            entry.put("cid", cid);
        }
        entry.put("event", "cb.transicao");
        entry.put("endpoint", endpoint);
        entry.put("de", fromState);
        entry.put("para", toState);
        writeLine(entry);
    }

    public void logCircuitBreakerRejected(String cid, String endpoint) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("svc", serviceName);
        if (cid != null && !cid.isBlank()) {
            entry.put("cid", cid);
        }
        entry.put("event", "cb.rejeitada");
        entry.put("endpoint", endpoint);
        writeLine(entry);
    }

    public void logDeadlineExceeded(String cid, String stage, long elapsedMs, long deadlineMs) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("svc", serviceName);
        if (cid != null && !cid.isBlank()) {
            entry.put("cid", cid);
        }
        entry.put("event", "prazo.excedido");
        entry.put("etapa", stage);
        entry.put("ms", elapsedMs);
        entry.put("limiteMs", deadlineMs);
        writeLine(entry);
    }

    public void logSaleUnknown(String cid, String flight, String day, String user, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("svc", serviceName);
        if (cid != null && !cid.isBlank()) {
            entry.put("cid", cid);
        }
        entry.put("event", "venda.desconhecida");
        entry.put("flight", flight);
        entry.put("day", day);
        entry.put("user", user);
        entry.put("motivo", reason);
        writeLine(entry);
    }

    public void logBonusNotCredited(String cid, String transactionId, String user, int bonus, String reason) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("svc", serviceName);
        if (cid != null && !cid.isBlank()) {
            entry.put("cid", cid);
        }
        entry.put("event", "bonus.nao_creditado");
        entry.put("transactionId", transactionId);
        entry.put("user", user);
        entry.put("bonus", bonus);
        entry.put("motivo", reason);
        writeLine(entry);
    }

    private void writeLine(Map<String, Object> entry) {
        try {
            System.out.println(objectMapper.writeValueAsString(entry));
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize log entry: " + e.getMessage());
        }
    }
}
