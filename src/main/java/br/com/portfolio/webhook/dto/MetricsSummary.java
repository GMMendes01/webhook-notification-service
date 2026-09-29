package br.com.portfolio.webhook.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record MetricsSummary(
        long totalEvents,
        long totalDeliveries,
        List<StatusCount> byStatus,
        double successRate,
        Double averageLatencyMs,
        Double p95LatencyMs,
        long deadLetterCount
) {
}
