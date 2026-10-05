package com.saurabh.banking.service;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.exception.InvalidTransactionException;
import com.saurabh.banking.transaction.Transaction;

public class BankingService {

    private final AuditLogger auditLogger;

    public BankingService(AuditLogger auditLogger) {
        if (auditLogger == null) {
            throw new IllegalArgumentException(
                    "AuditLogger cannot be null"
            );
        }

        this.auditLogger = auditLogger;
    }

    public void deposit(Account account, double amount) {

        validateAccount(account);

        if (amount <= 0) {
            throw new InvalidTransactionException(
                    "Deposit amount must be greater than zero"
            );
        }

        account.deposit(amount);

        auditLogger.log(
                "Deposit %.2f into account %d"
                        .formatted(
                                amount,
                                account.getAccountNumber()
                        )
        );
    }

    public void withdraw(Account account, double amount) {

        validateAccount(account);

        if (amount <= 0) {
            throw new InvalidTransactionException(
                    "Withdrawal amount must be greater than zero"
            );
        }

        account.withdraw(amount);

        auditLogger.log(
                "Withdrawal %.2f from account %d"
                        .formatted(
                                amount,
                                account.getAccountNumber()
                        )
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

        if (amount <= 0) {
            throw new InvalidTransactionException(
                    "Transfer amount must be greater than zero"
            );
        }

        if (type == null || type.isBlank()) {
            throw new InvalidTransactionException(
                    "Transfer type is required"
            );
        }

        Account first =
                from.getAccountNumber() < to.getAccountNumber()
                        ? from
                        : to;

        Account second =
                first == from
                        ? to
                        : from;

        first.getLock().lock();
        second.getLock().lock();

        try {

            from.withdraw(amount);
            to.deposit(amount);

        } finally {

            second.getLock().unlock();
            first.getLock().unlock();
        }

        Transaction transaction =
                Transaction.create(
                        from.getAccountNumber(),
                        to.getAccountNumber(),
                        amount,
                        type
                );

        auditLogger.log(
                "Transfer " + transaction.transactionId()
        );

        return transaction;
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