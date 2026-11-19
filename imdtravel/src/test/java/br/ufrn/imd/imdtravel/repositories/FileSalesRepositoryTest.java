package br.ufrn.imd.imdtravel.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.ufrn.imd.imdtravel.models.Sale;

class FileSalesRepositoryTest {

    @TempDir
    Path dir;

    private Sale sale(int i) {
        return new Sale("cid-" + i, "tx-" + i, "IMD100", "2026-12-01", "user" + i,
                new BigDecimal("350.50"), new BigDecimal("1927.75"), Instant.now());
    }

    @Test
    void shouldReturnEmptyListWhenNoSalesSaved() {
        assertTrue(new FileSalesRepository(dir.toString()).findAll().isEmpty());
    }

    @Test
    void shouldPersistAcrossInstances() {
        new FileSalesRepository(dir.toString()).save(sale(1));
        var all = new FileSalesRepository(dir.toString()).findAll();
        assertEquals(1, all.size());
        assertEquals("cid-1", all.get(0).correlationId());
        assertEquals(new BigDecimal("350.50"), all.get(0).valueUSD());
    }

    @Test
    void shouldKeepAllSalesWhenSavingConcurrently() throws Exception {
        var repo = new FileSalesRepository(dir.toString());
        ExecutorService ex = Executors.newFixedThreadPool(25);
        IntStream.range(0, 25).forEach(i -> ex.submit(() -> repo.save(sale(i))));
        ex.shutdown();
        assertTrue(ex.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(25, repo.findAll().size());
    }
}
