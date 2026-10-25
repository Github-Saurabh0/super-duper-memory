
package com.saurabh.banking.service;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.database.ConnectionProvider;
import com.saurabh.banking.database.DatabaseConnection;
import com.saurabh.banking.exception.InvalidTransactionException;
import com.saurabh.banking.repository.AccountRepository;
import com.saurabh.banking.repository.TransactionRepository;
import com.saurabh.banking.transaction.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class BankingService {

    private final AuditLogger auditLogger;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final ConnectionProvider connectionProvider;

    // Backward-compatible constructor: in-memory mode.
    public BankingService(AuditLogger auditLogger) {
        this(auditLogger, null, null, DatabaseConnection::getConnection);
    }

    // Backward-compatible constructor with transaction persistence.
    public BankingService(
            AuditLogger auditLogger,
            TransactionRepository transactionRepository) {
        this(
                auditLogger,
                transactionRepository,
                null,
                DatabaseConnection::getConnection);
    }

    // Database-enabled constructor.
    public BankingService(
            AuditLogger auditLogger,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository) {
        this(
                auditLogger,
                transactionRepository,
                accountRepository,
                DatabaseConnection::getConnection);
    }

    // Constructor with injectable connection provider for testing.
    public BankingService(
            AuditLogger auditLogger,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            ConnectionProvider connectionProvider) {

        this.auditLogger = Objects.requireNonNull(
                auditLogger,
                "AuditLogger cannot be null");

        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;

        this.connectionProvider = Objects.requireNonNull(
                connectionProvider,
                "ConnectionProvider cannot be null");

        if ((transactionRepository == null) != (accountRepository == null)) {
            throw new IllegalArgumentException(
                    "Both repositories must be provided for database mode");
        }
    }

    public void deposit(Account account, double amount) {
        validateAccount(account);
        validateAmount(amount, "Deposit");

        Account first = account;
        first.getLock().lock();

        BigDecimal previousBalance = account.getBalanceSnapshot();
        boolean persisted = false;

        try {
            account.deposit(amount);

            Transaction transaction = Transaction.create(
                    null,
                    account.getAccountNumber(),
                    amount,
                    "DEPOSIT");

            persisted = persistOperation(account, null, transaction);

            auditLogger.log(
                    "Deposit %.2f into account %d"
                            .formatted(amount, account.getAccountNumber()));

        } catch (RuntimeException exception) {
            if (!persisted) {
                account.restoreBalance(previousBalance);
            }
            throw exception;

        } finally {
            first.getLock().unlock();
        }
    }

    public void withdraw(Account account, double amount) {
        validateAccount(account);
        validateAmount(amount, "Withdrawal");

        account.getLock().lock();

        BigDecimal previousBalance = account.getBalanceSnapshot();
        boolean persisted = false;

        try {
            account.withdraw(amount);

            Transaction transaction = Transaction.create(
                    account.getAccountNumber(),
                    null,
                    amount,
                    "WITHDRAWAL");

            persisted = persistOperation(account, null, transaction);

            auditLogger.log(
                    "Withdrawal %.2f from account %d"
                            .formatted(amount, account.getAccountNumber()));

        } catch (RuntimeException exception) {
            if (!persisted) {
                account.restoreBalance(previousBalance);
            }
            throw exception;

        } finally {
            account.getLock().unlock();
        }
    }

    public Transaction transfer(
            Account from,
            Account to,
            double amount,
            String type) {

        validateAccount(from);
        validateAccount(to);

        if (from.getAccountNumber().equals(to.getAccountNumber())) {
            throw new InvalidTransactionException(
                    "Source and destination accounts must be different");
        }

        validateAmount(amount, "Transfer");

        if (type == null || type.isBlank()) {
            throw new InvalidTransactionException(
                    "Transfer type is required");
        }

        // Lock accounts in a consistent order to reduce deadlock risk.
        Account first = from.getAccountNumber() < to.getAccountNumber()
                ? from
                : to;

        Account second = first == from ? to : from;

        first.getLock().lock();
        second.getLock().lock();

        BigDecimal fromPrevious = from.getBalanceSnapshot();
        BigDecimal toPrevious = to.getBalanceSnapshot();
        boolean persisted = false;

        try {
            from.withdraw(amount);
            to.deposit(amount);

            Transaction transaction = Transaction.create(
                    from.getAccountNumber(),
                    to.getAccountNumber(),
                    amount,
                    type);

            persisted = persistOperation(from, to, transaction);

            auditLogger.log("Transfer " + transaction.transactionId());

            return transaction;

        } catch (RuntimeException exception) {
            if (!persisted) {
                from.restoreBalance(fromPrevious);
                to.restoreBalance(toPrevious);
            }
            throw exception;

        } finally {
            second.getLock().unlock();
            first.getLock().unlock();
        }
    }

    private boolean persistOperation(
            Account firstAccount,
            Account secondAccount,
            Transaction transaction) {

        // In-memory mode: no database persistence is configured.
        if (transactionRepository == null) {
            return false;
        }

        Connection connection = null;
        boolean committed = false;
        RuntimeException operationFailure = null;

        try {
            connection = connectionProvider.getConnection();
            connection.setAutoCommit(false);

            accountRepository.updateBalance(
                    connection,
                    firstAccount.getAccountNumber(),
                    BigDecimal.valueOf(firstAccount.getBalance()));

            if (secondAccount != null) {
                accountRepository.updateBalance(
                        connection,
                        secondAccount.getAccountNumber(),
                        BigDecimal.valueOf(secondAccount.getBalance()));
            }

            transactionRepository.save(connection, transaction);

            connection.commit();
            committed = true;

            return true;

        } catch (SQLException | RuntimeException exception) {

            if (connection != null && !committed) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }

            operationFailure = new IllegalStateException(
                    "Database transaction failed",
                    exception);

            throw operationFailure;

        } finally {
            if (connection != null) {
                try {
                    connection.close();

                } catch (SQLException closeException) {
                    if (!committed) {
                        IllegalStateException closeFailure = new IllegalStateException(
                                "Failed to close database connection",
                                closeException);

                        if (operationFailure != null) {
                            operationFailure.addSuppressed(closeFailure);
                        } else {
                            throw closeFailure;
                        }
                    }

                    // If commit succeeded, don't report failure only because
                    // connection closing failed.
                }
            }
        }
    }

    private void validateAmount(double amount, String operation) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new InvalidTransactionException(
                    operation
                            + " amount must be finite and greater than zero");
        }
    }

    private void validateAccount(Account account) {
        if (account == null) {
            throw new InvalidTransactionException(
                    "Account cannot be null");
        }

        if (account.getAccountNumber() == null) {
            throw new InvalidTransactionException(
                    "Account number cannot be null");
        }
    }
}
