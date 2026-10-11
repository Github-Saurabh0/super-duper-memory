
package com.saurabh.banking.audit;

import com.saurabh.banking.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditLogger {

    private static final String INSERT_SQL = """
            INSERT INTO audit_logs (event_text, created_at)
            VALUES (?, ?)
            """;

    private final CopyOnWriteArrayList<String> events =
            new CopyOnWriteArrayList<>();

    public void log(String event) {
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException(
                    "Audit event cannot be empty"
            );
        }

        LocalDateTime timestamp = LocalDateTime.now();
        String message = "[%s] %s".formatted(timestamp, event);

        // Preserve existing in-memory and console logging.
        events.add(message);
        System.out.println(message);

        // Persist the audit event in MySQL.
        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            statement.setString(1, message);
            statement.setTimestamp(
                    2, Timestamp.valueOf(timestamp)
            );

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to persist audit event", e
            );
        }
    }

    public CopyOnWriteArrayList<String> getEvents() {
        return events;
    }
}
