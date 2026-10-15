
package com.saurabh.banking.service;

import com.saurabh.banking.repository.FraudAlertRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class FraudAnalyticsService {

    private final Supplier<Map<String, Long>> countProvider;

    public FraudAnalyticsService(FraudAlertRepository repository) {
        this(Objects.requireNonNull(
                repository, "FraudAlertRepository cannot be null"
        )::countByRiskLevel);
    }

    // Package-private constructor for unit testing.
    FraudAnalyticsService(Supplier<Map<String, Long>> countProvider) {
        this.countProvider = Objects.requireNonNull(
                countProvider, "Count provider cannot be null"
        );
    }

    public Map<String, Long> generateReport() {
        Map<String, Long> counts = countProvider.get();

        long highRisk = counts.getOrDefault("HIGH_RISK", 0L);
        long mediumRisk = counts.getOrDefault("MEDIUM_RISK", 0L);

        Map<String, Long> report = new LinkedHashMap<>();
        report.put("HIGH_RISK", highRisk);
        report.put("MEDIUM_RISK", mediumRisk);
        report.put("TOTAL_ALERTS", highRisk + mediumRisk);

        return report;
    }
}
