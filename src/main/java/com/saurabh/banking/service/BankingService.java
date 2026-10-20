
package com.saurabh.banking.service;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.audit.AuditLogger;
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

    // Backward-compatible constructor: in-memory mode.
    public BankingService(AuditLogger auditLogger) {
        this(auditLogger, null, null);
    }

    // Backward-compatible constructor with transaction persistence.
    public BankingService(
            AuditLogger auditLogger,
            TransactionRepository transactionRepository
    ) {
        this(auditLogger, transactionRepository, null);
    }

    // Database-enabled constructor.
    public BankingService(
        AuditLogger auditLogger,
        TransactionRepository transactionRepository,
        AccountRepository accountRepository
) {
        this.auditLogger = Objects.requireNonNull(
                auditLogger,
                "AuditLogger cannot be null"
        );
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;

        if ((transactionRepository == null) != (accountRepository == null)) {
            throw new IllegalArgumentException(
                    "Both repositories must be provided for database mode"
            );
        }
    }

    public void deposit(Account account, double amount) {
        validateAccount(account);
        validateAmount(amount, "Deposit");

        Account first = account;
        first.getLock().lock();

        BigDecimal previousBalance = account.getBalanceSnapshot();

        try {
            account.deposit(amount);

            Transaction transaction = Transaction.create(
                    null,
                    account.getAccountNumber(),
                    amount,
                    "DEPOSIT"
            );

            persistOperation(account, null, transaction);

            auditLogger.log(
                    "Deposit %.2f into account %d"
                            .formatted(amount, account.getAccountNumber())
            );
        } catch (RuntimeException exception) {
            account.restoreBalance(previousBalance);
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

        try {
            account.withdraw(amount);

            Transaction transaction = Transaction.create(
                    account.getAccountNumber(),
                    null,
                    amount,
                    "WITHDRAWAL"
            );

            persistOperation(account, null, transaction);

            auditLogger.log(
                    "Withdrawal %.2f from account %d"
                            .formatted(amount, account.getAccountNumber())
            );
        } catch (RuntimeException exception) {
            account.restoreBalance(previousBalance);
            throw exception;
        } finally {
            account.getLock().unlock();
        }
    }

    public Transaction transfer(
            Account from,
            Account to,
            double amount,
            String type
    ) {
        validateAccount(from);
        validateAccount(to);

        if (from.getAccountNumber().equals(to.getAccountNumber())) {
            throw new InvalidTransactionException(
                    "Source and destination accounts must be different"
            );
        }

        validateAmount(amount, "Transfer");

        if (type == null || type.isBlank()) {
            throw new InvalidTransactionException(
                    "Transfer type is required"
            );
        }

        Account first = from.getAccountNumber() < to.getAccountNumber()
                ? from : to;
        Account second = first == from ? to : from;

        first.getLock().lock();
        second.getLock().lock();

        BigDecimal fromPrevious = from.getBalanceSnapshot();
        BigDecimal toPrevious = to.getBalanceSnapshot();

        try {
            from.withdraw(amount);
            to.deposit(amount);

            Transaction transaction = Transaction.create(
                    from.getAccountNumber(),
                    to.getAccountNumber(),
                    amount,
                    type
            );

            persistOperation(from, to, transaction);

            auditLogger.log(
                    "Transfer " + transaction.transactionId()
            );

            return transaction;
        } catch (RuntimeException exception) {
            from.restoreBalance(fromPrevious);
            to.restoreBalance(toPrevious);
            throw exception;
        } finally {
            second.getLock().unlock();
            first.getLock().unlock();
        }
    }

    private void persistOperation(
            Account firstAccount,
            Account secondAccount,
            Transaction transaction
    ) {
        // In-memory mode preserves compatibility with existing tests.
        if (transactionRepository == null) {
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try {
                accountRepository.updateBalance(
                        connection,
                        firstAccount.getAccountNumber(),
                        BigDecimal.valueOf(firstAccount.getBalance())
                );

                if (secondAccount != null) {
                    accountRepository.updateBalance(
                            connection,
                            secondAccount.getAccountNumber(),
                            BigDecimal.valueOf(secondAccount.getBalance())
                    );
                }

                transactionRepository.save(connection, transaction);

                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw new IllegalStateException(
                        "Database transaction failed",
                        exception
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to open or close database transaction",
                    exception
            );
        }
    }

    private void validateAmount(double amount, String operation) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new InvalidTransactionException(
                    operation
                            + " amount must be finite and greater than zero"
            );
        }
    }

    private void validateAccount(Account account) {
        if (account == null) {
            throw new InvalidTransactionException(
                    "Account cannot be null"
            );
        }

        if (account.getAccountNumber() == null) {
            throw new InvalidTransactionException(
                    "Account number cannot be null"
            );
        }
    }
}
