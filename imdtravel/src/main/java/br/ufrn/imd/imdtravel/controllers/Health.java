package br.ufrn.imd.imdtravel.controllers;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Health {

    private final Path salesDir;

    public Health(@Value("${imdtravel.sales-dir:/data}") String salesDir) {
        this.salesDir = Path.of(salesDir);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        long uptimeS = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        String errorReason = checkSalesDirWritable();

        Map<String, Object> body = new LinkedHashMap<>();
        if (errorReason == null) {
            body.put("status", "ok");
            body.put("uptimeS", uptimeS);
            return ResponseEntity.ok(body);
        }

        body.put("status", "down");
        body.put("uptimeS", uptimeS);
        body.put("reason", errorReason);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private String checkSalesDirWritable() {
        try {
            Files.createDirectories(salesDir);
            Files.writeString(
                    salesDir.resolve(".health"),
                    Long.toString(System.currentTimeMillis()));
            return null;
        } catch (IOException e) {
            return "diretório de vendas sem escrita: " + e.getMessage();
        }
    }
}
