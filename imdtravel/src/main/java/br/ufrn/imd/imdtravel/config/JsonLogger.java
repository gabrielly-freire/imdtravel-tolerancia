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

    private void writeLine(Map<String, Object> entry) {
        try {
            System.out.println(objectMapper.writeValueAsString(entry));
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize log entry: " + e.getMessage());
        }
    }
}
