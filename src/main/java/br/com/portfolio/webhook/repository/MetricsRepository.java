package br.com.portfolio.webhook.repository;

import br.com.portfolio.webhook.dto.MetricsSummary;
import br.com.portfolio.webhook.dto.StatusCount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Aggregate queries over the deliveries ledger, powering {@code GET /api/metrics/summary}.
 * Kept as native SQL so percentile_cont (Postgres) can be used directly.
 */
@Repository
public class MetricsRepository {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public List<StatusCount> countByStatus() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT status, COUNT(*) FROM deliveries GROUP BY status ORDER BY status
                """).getResultList();
        return rows.stream()
                .map(r -> new StatusCount((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    public long totalDeliveries() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM deliveries").getSingleResult()).longValue();
    }

    public long totalEvents() {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM events").getSingleResult()).longValue();
    }

    public Double averageLatencyMs() {
        Object value = em.createNativeQuery(
                "SELECT AVG(response_ms) FROM deliveries WHERE response_ms IS NOT NULL").getSingleResult();
        return toDouble(value);
    }

    public Double p95LatencyMs() {
        Object value = em.createNativeQuery("""
                SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY response_ms)
                FROM deliveries WHERE response_ms IS NOT NULL
                """).getSingleResult();
        return toDouble(value);
    }

    public long deadLetterCount() {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM deliveries WHERE status = 'DEAD_LETTER'").getSingleResult()).longValue();
    }

    public long successCount() {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM deliveries WHERE status = 'SUCCESS'").getSingleResult()).longValue();
    }

    public MetricsSummary buildSummary() {
        long total = totalDeliveries();
        long success = successCount();
        double successRate = total == 0 ? 0.0 : (double) success / total;
        return MetricsSummary.builder()
                .totalEvents(totalEvents())
                .totalDeliveries(total)
                .byStatus(countByStatus())
                .successRate(round(successRate))
                .averageLatencyMs(round(averageLatencyMs()))
                .p95LatencyMs(round(p95LatencyMs()))
                .deadLetterCount(deadLetterCount())
                .build();
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        return ((Number) value).doubleValue();
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }
}
