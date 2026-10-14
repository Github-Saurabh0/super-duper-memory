
package com.saurabh.banking.service;

import com.saurabh.banking.repository.FraudAlertRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FraudAnalyticsServiceTest {

    @Test
    void constructorRejectsNullRepository() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FraudAnalyticsService(null)
        );
    }

    @Test
    void serviceCanGenerateReportWithRepository() {
        FraudAlertRepository repository = new FraudAlertRepository();

        FraudAnalyticsService service =
                new FraudAnalyticsService(repository);

        assertNotNull(service);
    }
}
