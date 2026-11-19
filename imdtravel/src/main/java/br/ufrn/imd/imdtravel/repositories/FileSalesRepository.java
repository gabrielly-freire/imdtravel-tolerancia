package br.ufrn.imd.imdtravel.repositories;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufrn.imd.imdtravel.models.Sale;

/**
 * Grava as vendas em um arquivo JSON Lines (uma venda por linha) dentro de SALES_DIR,
 * que é o volume nomeado. Cada gravação é um único append com lock, então chamadas
 * simultâneas não misturam linhas; o arquivo sobrevive a `docker compose down`.
 */
@Repository
public class FileSalesRepository implements SalesRepository {

    private static final String FILE_NAME = "sales.jsonl";
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper mapper = new ObjectMapper();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path file;

    public FileSalesRepository(@Value("${imdtravel.sales-dir:/data}") String salesDir) {
        this.file = Path.of(salesDir).resolve(FILE_NAME);
    }

    @Override
    public void save(Sale sale) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("correlationId", sale.correlationId());
        row.put("transactionId", sale.transactionId());
        row.put("flight", sale.flight());
        row.put("day", sale.day());
        row.put("user", sale.user());
        row.put("valueUSD", sale.valueUSD().toPlainString());
        row.put("valueBRL", sale.valueBRL().toPlainString());
        row.put("ts", sale.ts().toString());

        lock.writeLock().lock();
        try {
            String line = mapper.writeValueAsString(row) + System.lineSeparator();
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar a venda", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Sale> findAll() {
        lock.readLock().lock();
        try {
            if (!Files.exists(file)) {
                return List.of();
            }
            List<Sale> sales = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, String> r = mapper.readValue(line, MAP_TYPE);
                sales.add(new Sale(
                        r.get("correlationId"), r.get("transactionId"), r.get("flight"),
                        r.get("day"), r.get("user"),
                        new BigDecimal(r.get("valueUSD")), new BigDecimal(r.get("valueBRL")),
                        Instant.parse(r.get("ts"))));
            }
            return sales;
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler as vendas", e);
        } finally {
            lock.readLock().unlock();
        }
    }
}
