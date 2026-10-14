package com.saurabh.banking.repository;

import com.saurabh.banking.database.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FraudAlertRepositoryTest {

    private final FraudAlertRepository repository =
            new FraudAlertRepository();

    @Test
    void saveRejectsNullTransactionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(null, "HIGH_RISK")
        );
    }

    @Test
    void saveRejectsBlankTransactionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(" ", "HIGH_RISK")
        );
    }

    @Test
    void saveRejectsApprovedRisk() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save("TEST-001", "APPROVED")
        );
    }

    @Test
    void saveRejectsUnknownRiskLevel() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save("TEST-002", "UNKNOWN")
        );
    }

    @Test
    void savesAndRetrievesHighRiskAlert() throws Exception {
        String transactionId =
                "DAY8-" + UUID.randomUUID();

        try {
            repository.save(transactionId, "HIGH_RISK");

            try (Connection connection =
                         DatabaseConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement("""
                                 SELECT transaction_id, risk_level
                                 FROM fraud_alerts
                                 WHERE transaction_id = ?
                                 """)) {

                statement.setString(1, transactionId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(
                            resultSet.next(),
                            "Fraud alert should exist in the database"
                    );
                    assertEquals(
                            transactionId,
                            resultSet.getString("transaction_id")
                    );
                    assertEquals(
                            "HIGH_RISK",
                            resultSet.getString("risk_level")
                    );
                }
            }
        } finally {
            try (Connection connection =
                         DatabaseConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement("""
                                 DELETE FROM fraud_alerts
                                 WHERE transaction_id = ?
                                 """)) {
                statement.setString(1, transactionId);
                statement.executeUpdate();
            }
        }
    }

@Test
void countByRiskLevelIncludesSavedAlerts() throws Exception {
    String highRiskId = "DAY10-H-" + UUID.randomUUID();
    String mediumRiskId = "DAY10-M-" + UUID.randomUUID();

    try {
        repository.save(highRiskId, "HIGH_RISK");
        repository.save(mediumRiskId, "MEDIUM_RISK");

        Map<String, Long> counts = repository.countByRiskLevel();

        org.junit.jupiter.api.Assertions.assertTrue(
                counts.get("HIGH_RISK") >= 1,
                "At least one high-risk alert should exist"
        );

        org.junit.jupiter.api.Assertions.assertTrue(
                counts.get("MEDIUM_RISK") >= 1,
                "At least one medium-risk alert should exist"
        );

    } finally {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM fraud_alerts WHERE transaction_id IN (?, ?)")) {

            statement.setString(1, highRiskId);
            statement.setString(2, mediumRiskId);
            statement.executeUpdate();
        }
    }
}

}
