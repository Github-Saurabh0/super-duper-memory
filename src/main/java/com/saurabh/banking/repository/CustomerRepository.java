package com.saurabh.banking.repository;

import com.saurabh.banking.customer.Customer;
import com.saurabh.banking.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CustomerRepository implements Repository<Long, Customer> {

    private static final String INSERT_SQL = """
            INSERT INTO customers (id, name, email, phone)
            VALUES (?, ?, ?, ?)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT id, name, email, phone
            FROM customers
            WHERE id = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT id, name, email, phone
            FROM customers
            ORDER BY id
            """;

    private static final String DELETE_SQL = """
            DELETE FROM customers
            WHERE id = ?
            """;

    @Override
    public Customer findById(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Customer ID cannot be null"
            );
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_BY_ID_SQL)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return mapCustomer(resultSet);
                }

                return null;
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find customer: " + id,
                    exception
            );
        }
    }

    @Override
    public List<Customer> findAll() {

        List<Customer> customers = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                customers.add(mapCustomer(resultSet));
            }

            return customers;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to retrieve customers",
                    exception
            );
        }
    }

    @Override
    public void save(Customer customer) {

        if (customer == null) {
            throw new IllegalArgumentException(
                    "Customer cannot be null"
            );
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            statement.setLong(1, customer.getId());
            statement.setString(2, customer.getName());
            statement.setString(3, customer.getEmail());
            statement.setString(4, customer.getPhone());

            statement.executeUpdate();

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to save customer: "
                            + customer.getEmail(),
                    exception
            );
        }
    }

    @Override
    public void delete(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Customer ID cannot be null"
            );
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(DELETE_SQL)) {

            statement.setLong(1, id);
            statement.executeUpdate();

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to delete customer: " + id,
                    exception
            );
        }
    }

    private Customer mapCustomer(ResultSet resultSet)
            throws SQLException {

        return new Customer(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("email"),
                resultSet.getString("phone")
        );
    }
}