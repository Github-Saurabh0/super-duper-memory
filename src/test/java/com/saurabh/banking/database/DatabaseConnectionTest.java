package com.saurabh.banking.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatabaseConnectionTest {

    @Test
    void shouldCreateDatabaseConnection() throws Exception {

        try (Connection connection =
                     DatabaseConnection.getConnection()) {

            assertNotNull(connection);
        }
    }
}