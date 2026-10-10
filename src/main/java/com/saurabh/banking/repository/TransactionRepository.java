
package com.saurabh.banking.repository;

import com.saurabh.banking.database.DatabaseConnection;
import com.saurabh.banking.transaction.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class TransactionRepository
        implements Repository<String, Transaction> {

    private static final String INSERT_SQL = """
            INSERT INTO transactions
                (transaction_id, from_account, to_account,
                 amount, transaction_type, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT transaction_id, from_account, to_account,
                   amount, transaction_type, created_at
            FROM transactions
            WHERE transaction_id = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT transaction_id, from_account, to_account,
                   amount, transaction_type, created_at
            FROM transactions
            ORDER BY created_at DESC
            """;

    private static final String DELETE_SQL =
            "DELETE FROM transactions WHERE transaction_id = ?";

    @Override
    public Transaction findById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Transaction ID cannot be empty"
            );
        }

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_BY_ID_SQL)) {

            statement.setString(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapTransaction(resultSet);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to find transaction: " + id, e
            );
        }
    }

    @Override
    public List<Transaction> findAll() {
        List<Transaction> transactions = new ArrayList<>();

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                transactions.add(mapTransaction(resultSet));
            }

            return transactions;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to retrieve transactions", e
            );
        }
    }

    @Override
    public void save(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException(
                    "Transaction cannot be null"
            );
        }

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            statement.setString(1, transaction.transactionId());

            if (transaction.fromAccount() == null) {
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(2, transaction.fromAccount());
            }

            if (transaction.toAccount() == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, transaction.toAccount());
            }

            statement.setBigDecimal(
                    4,
                    java.math.BigDecimal.valueOf(transaction.amount())
            );

            statement.setString(5, transaction.type());

            statement.setTimestamp(
                    6,
                    Timestamp.valueOf(transaction.timestamp())
            );

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to save transaction: "
                            + transaction.transactionId(), e
            );
        }
    }

    @Override
    public void delete(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Transaction ID cannot be empty"
            );
        }

        try (Connection connection =
                     DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(DELETE_SQL)) {

            statement.setString(1, id);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to delete transaction: " + id, e
            );
        }
    }

    private Transaction mapTransaction(ResultSet resultSet)
            throws SQLException {

        Timestamp timestamp = resultSet.getTimestamp("created_at");

        return new Transaction(
                resultSet.getString("transaction_id"),
                getNullableLong(resultSet, "from_account"),
                getNullableLong(resultSet, "to_account"),
                resultSet.getBigDecimal("amount").doubleValue(),
                resultSet.getString("transaction_type"),
                timestamp.toLocalDateTime()
        );
    }

    private Long getNullableLong(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {

        long value = resultSet.getLong(columnName);

        return resultSet.wasNull() ? null : value;
    }
}
