package br.com.portfolio.webhook.service;

import br.com.portfolio.webhook.dto.MetricsSummary;
import br.com.portfolio.webhook.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricsRepository metricsRepository;

    @Transactional(readOnly = true)
    public MetricsSummary summary() {
        return metricsRepository.buildSummary();
    }
}
