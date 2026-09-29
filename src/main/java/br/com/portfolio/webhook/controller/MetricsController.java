package br.com.portfolio.webhook.controller;

import br.com.portfolio.webhook.dto.MetricsSummary;
import br.com.portfolio.webhook.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /** Aggregates over the deliveries ledger. Raw Micrometer metrics live at /actuator/metrics. */
    @GetMapping("/summary")
    public MetricsSummary summary() {
        return metricsService.summary();
    }
}
