
package com.saurabh.banking.service;

import com.saurabh.banking.repository.FraudAlertRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FraudAnalyticsServiceTest {

    @Test
    void constructorRejectsNullRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new FraudAnalyticsService((FraudAlertRepository) null)
        );
    }

    @Test
    void generateReportCalculatesRiskCountsAndTotal() {
        FraudAnalyticsService service = new FraudAnalyticsService(
                () -> Map.of("HIGH_RISK", 2L, "MEDIUM_RISK", 3L)
        );

        Map<String, Long> report = service.generateReport();

        assertEquals(2L, report.get("HIGH_RISK"));
        assertEquals(3L, report.get("MEDIUM_RISK"));
        assertEquals(5L, report.get("TOTAL_ALERTS"));
    }

    @Test
    void generateReportDefaultsMissingCountsToZero() {
        FraudAnalyticsService service =
                new FraudAnalyticsService(Map::of);

        Map<String, Long> report = service.generateReport();

        assertEquals(0L, report.get("HIGH_RISK"));
        assertEquals(0L, report.get("MEDIUM_RISK"));
        assertEquals(0L, report.get("TOTAL_ALERTS"));
    }

    @Test
    void constructorRejectsNullCountProvider() {
        assertThrows(
                NullPointerException.class,
                () -> new FraudAnalyticsService(
                        (java.util.function.Supplier<Map<String, Long>>) null
                )
        );
    }
}
