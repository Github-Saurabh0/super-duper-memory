package com.saurabh.banking.service;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.transaction.Transaction;

public class BankingService {
    private final AuditLogger auditLogger;

    public BankingService(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public void deposit(Account account, double amount) {
        account.deposit(amount);
        auditLogger.log("Deposit %.2f into account %d".formatted(amount, account.getAccountNumber()));
    }

    public void withdraw(Account account, double amount) {
        account.withdraw(amount);
        auditLogger.log("Withdrawal %.2f from account %d".formatted(amount, account.getAccountNumber()));
    }

    public Transaction transfer(Account from, Account to, double amount, String type) {
        if (from == to) {
            from.withdraw(amount);
            from.deposit(amount);
        } else {
            Account first = from.getAccountNumber() < to.getAccountNumber() ? from : to;
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
        }

        Transaction transaction = Transaction.create(
                from.getAccountNumber(), to.getAccountNumber(), amount, type);
        auditLogger.log("Transfer " + transaction.transactionId());
        return transaction;
    }
}
