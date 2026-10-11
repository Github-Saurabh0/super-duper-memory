
package com.saurabh.banking.audit;

import com.saurabh.banking.database.DatabaseConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerTest {

    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = new AuditLogger();
    }

    @Test
    void shouldStoreEventInMemoryAndDatabase() throws SQLException {
        String event = "DAY7_TEST_" + System.nanoTime();

        auditLogger.log(event);

        assertEquals(1, auditLogger.getEvents().size());
        assertTrue(auditLogger.getEvents().get(0).contains(event));

        String sql = """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE event_text LIKE ?
                """;

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, "%" + event + "%");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }
        } finally {
            deleteTestEvent(event);
        }
    }

    @Test
    void shouldRejectNullEvent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> auditLogger.log(null)
        );
    }

    @Test
    void shouldRejectBlankEvent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> auditLogger.log(" ")
        );
    }

    private void deleteTestEvent(String event) throws SQLException {
        String sql = """
                DELETE FROM audit_logs
                WHERE event_text LIKE ?
                """;

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, "%" + event + "%");
            statement.executeUpdate();
        }
    }
}
