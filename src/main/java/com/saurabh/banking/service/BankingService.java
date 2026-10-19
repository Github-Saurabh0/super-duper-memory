
package com.saurabh.banking.service;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.exception.InvalidTransactionException;
import com.saurabh.banking.repository.TransactionRepository;
import com.saurabh.banking.transaction.Transaction;

import java.util.Objects;

public class BankingService {

    private final AuditLogger auditLogger;
    private final TransactionRepository transactionRepository;

    // Backward-compatible constructor
    public BankingService(AuditLogger auditLogger) {
        this(auditLogger, null);
    }

    // Constructor for transaction persistence
    public BankingService(
            AuditLogger auditLogger,
            TransactionRepository transactionRepository
    ) {
        this.auditLogger = Objects.requireNonNull(
                auditLogger,
                "AuditLogger cannot be null"
        );
        this.transactionRepository = transactionRepository;
    }

    public void deposit(Account account, double amount) {
        validateAccount(account);
        validateAmount(amount, "Deposit");

        account.deposit(amount);

        Transaction transaction = Transaction.create(
                null,
                account.getAccountNumber(),
                amount,
                "DEPOSIT"
        );

        persistTransaction(transaction);

        auditLogger.log(
                "Deposit %.2f into account %d"
                        .formatted(amount, account.getAccountNumber())
        );
    }

    public void withdraw(Account account, double amount) {
        validateAccount(account);
        validateAmount(amount, "Withdrawal");

        account.withdraw(amount);

        Transaction transaction = Transaction.create(
                account.getAccountNumber(),
                null,
                amount,
                "WITHDRAWAL"
        );

        persistTransaction(transaction);

        auditLogger.log(
                "Withdrawal %.2f from account %d"
                        .formatted(amount, account.getAccountNumber())
        );
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

        Account first =
                from.getAccountNumber() < to.getAccountNumber()
                        ? from : to;
        Account second = first == from ? to : from;

        first.getLock().lock();
        second.getLock().lock();

        try {
            from.withdraw(amount);
            to.deposit(amount);
        } finally {
            second.getLock().unlock();
            first.getLock().unlock();
        }

        Transaction transaction = Transaction.create(
                from.getAccountNumber(),
                to.getAccountNumber(),
                amount,
                type
        );

        persistTransaction(transaction);

        auditLogger.log(
                "Transfer " + transaction.transactionId()
        );

        return transaction;
    }

    private void persistTransaction(Transaction transaction) {
        if (transactionRepository != null) {
            transactionRepository.save(transaction);
        }
    }

    private void validateAmount(double amount, String operation) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new InvalidTransactionException(
                    operation + " amount must be finite and greater than zero"
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
