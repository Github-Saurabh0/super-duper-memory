
package com.saurabh.banking.repository;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.account.CurrentAccount;
import com.saurabh.banking.account.SavingsAccount;
import com.saurabh.banking.customer.Customer;
import com.saurabh.banking.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AccountRepository implements Repository<Long, Account> {

    private static final String INSERT_SQL = """
            INSERT INTO accounts
                (account_number, customer_id, account_type, balance)
            VALUES (?, ?, ?, ?)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT account_number, customer_id, account_type, balance
            FROM accounts
            WHERE account_number = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT account_number, customer_id, account_type, balance
            FROM accounts
            ORDER BY account_number
            """;

    private static final String DELETE_SQL = """
            DELETE FROM accounts
            WHERE account_number = ?
            """;

    private final CustomerRepository customerRepository;

    public AccountRepository() {
        this(new CustomerRepository());
    }

    public AccountRepository(CustomerRepository customerRepository) {
        if (customerRepository == null) {
            throw new IllegalArgumentException(
                    "CustomerRepository cannot be null"
            );
        }
        this.customerRepository = customerRepository;
    }

    @Override
    public void save(Account account) {

        if (account == null) {
            throw new IllegalArgumentException(
                    "Account cannot be null"
            );
        }

        Customer owner = account.getOwner();

        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException(
                    "Account owner and customer ID are required"
            );
        }

        String accountType = getAccountType(account);

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            statement.setLong(1, account.getAccountNumber());
            statement.setLong(2, owner.getId());
            statement.setString(3, accountType);
            statement.setBigDecimal(
                    4,
                    java.math.BigDecimal.valueOf(account.getBalance())
            );

            statement.executeUpdate();

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to save account: "
                            + account.getAccountNumber(),
                    exception
            );
        }
    }

    @Override
    public Account findById(Long accountNumber) {

        if (accountNumber == null) {
            throw new IllegalArgumentException(
                    "Account number cannot be null"
            );
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_BY_ID_SQL)) {

            statement.setLong(1, accountNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapAccount(resultSet);
                }
                return null;
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to find account: " + accountNumber,
                    exception
            );
        }
    }

    @Override
    public List<Account> findAll() {

        List<Account> accounts = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                accounts.add(mapAccount(resultSet));
            }

            return accounts;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to retrieve accounts",
                    exception
            );
        }
    }

    @Override
    public void delete(Long accountNumber) {

        if (accountNumber == null) {
            throw new IllegalArgumentException(
                    "Account number cannot be null"
            );
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(DELETE_SQL)) {

            statement.setLong(1, accountNumber);
            statement.executeUpdate();

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to delete account: " + accountNumber,
                    exception
            );
        }
    }

    private String getAccountType(Account account) {

        if (account instanceof SavingsAccount) {
            return "SAVINGS";
        }

        if (account instanceof CurrentAccount) {
            return "CURRENT";
        }

        throw new IllegalArgumentException(
                "Unsupported account type: "
                        + account.getClass().getName()
        );
    }

    private Account mapAccount(ResultSet resultSet)
            throws SQLException {

        long accountNumber =
                resultSet.getLong("account_number");

        long customerId =
                resultSet.getLong("customer_id");

        String accountType =
                resultSet.getString("account_type");

        double balance =
                resultSet.getBigDecimal("balance").doubleValue();

        Customer owner = customerRepository.findById(customerId);

        if (owner == null) {
            throw new IllegalStateException(
                    "Customer not found for account: " + accountNumber
            );
        }

        return switch (accountType) {
            case "SAVINGS" ->
                    new SavingsAccount(accountNumber, owner, balance);

            case "CURRENT" ->
                    new CurrentAccount(accountNumber, owner, balance);

            default ->
                    throw new IllegalStateException(
                            "Unknown account type: " + accountType
                    );
        };
    }
}
