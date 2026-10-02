package com.saurabh.banking.account;

import com.saurabh.banking.customer.Customer;

import java.math.BigDecimal;
import java.util.concurrent.locks.ReentrantLock;

public abstract class Account {
    protected final Long accountNumber;
    protected final Customer owner;
    protected BigDecimal balance;
    protected final ReentrantLock lock = new ReentrantLock();

    protected Account(Long accountNumber, Customer owner, double openingBalance) {
        if (openingBalance < 0) throw new IllegalArgumentException("Opening balance cannot be negative");
        this.accountNumber = accountNumber;
        this.owner = owner;
        this.balance = BigDecimal.valueOf(openingBalance);
    }

    public abstract void withdraw(double amount);

    public void deposit(double amount) {
        validateAmount(amount);
        lock.lock();
        try {
            balance = balance.add(BigDecimal.valueOf(amount));
        } finally {
            lock.unlock();
        }
    }

    protected void validateAmount(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
    }

    public Long getAccountNumber() { return accountNumber; }
    public Customer getOwner() { return owner; }
    public double getBalance() { return balance.doubleValue(); }
    public ReentrantLock getLock() { return lock; }
}
