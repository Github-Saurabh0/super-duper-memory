
package com.saurabh.banking.service;

import com.saurabh.banking.repository.FraudAlertRepository;

import java.util.LinkedHashMap;
import java.util.Map;

public class FraudAnalyticsService {

    private final FraudAlertRepository repository;

    public FraudAnalyticsService(FraudAlertRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException(
                    "FraudAlertRepository cannot be null"
            );
        }
        this.repository = repository;
    }

    public Map<String, Long> generateReport() {
        Map<String, Long> counts = repository.countByRiskLevel();

        long highRisk = counts.getOrDefault("HIGH_RISK", 0L);
        long mediumRisk = counts.getOrDefault("MEDIUM_RISK", 0L);

        Map<String, Long> report = new LinkedHashMap<>();
        report.put("HIGH_RISK", highRisk);
        report.put("MEDIUM_RISK", mediumRisk);
        report.put("TOTAL_ALERTS", highRisk + mediumRisk);

        return report;
    }
}
