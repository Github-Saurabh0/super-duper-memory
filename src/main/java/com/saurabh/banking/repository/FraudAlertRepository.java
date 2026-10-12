package com.saurabh.banking.repository;

import com.saurabh.banking.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;



public class FraudAlertRepository {

    private static final String INSERT_SQL = """
            INSERT INTO fraud_alerts (transaction_id, risk_level)
            VALUES (?, ?)
            """;

    private static final String FIND_ALL_SQL = """
            SELECT id, transaction_id, risk_level, created_at
            FROM fraud_alerts
            ORDER BY created_at DESC, id DESC
            """;

    public void save(String transactionId, String riskLevel) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Transaction ID cannot be empty"
            );
        }

        if (!"HIGH_RISK".equals(riskLevel)
                && !"MEDIUM_RISK".equals(riskLevel)) {
            throw new IllegalArgumentException(
                    "Only HIGH_RISK and MEDIUM_RISK alerts can be saved"
            );
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            statement.setString(1, transactionId);
            statement.setString(2, riskLevel);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to save fraud alert for transaction: "
                            + transactionId, e
            );
        }
    }

    public List<String> findAll() {
        List<String> alerts = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                alerts.add(
                        "id=" + resultSet.getLong("id")
                                + ", transactionId="
                                + resultSet.getString("transaction_id")
                                + ", riskLevel="
                                + resultSet.getString("risk_level")
                                + ", createdAt="
                                + resultSet.getTimestamp("created_at")
                );
            }

            return alerts;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to retrieve fraud alerts", e
            );
        }
    }
}
