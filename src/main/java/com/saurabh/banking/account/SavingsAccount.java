package com.saurabh.banking.account;

import com.saurabh.banking.customer.Customer;
import java.math.BigDecimal;

public class SavingsAccount extends Account {
    private static final double INTEREST_RATE = 0.04;

    public SavingsAccount(Long accountNumber, Customer owner, double openingBalance) {
        super(accountNumber, owner, openingBalance);
    }

    @Override
    public void withdraw(double amount) {
        validateAmount(amount);
        lock.lock();
        try {
            BigDecimal requested = BigDecimal.valueOf(amount);
            if (requested.compareTo(balance) > 0) {
                throw new IllegalStateException("Insufficient balance");
            }
            balance = balance.subtract(requested);
        } finally {
            lock.unlock();
        }
    }

    public double calculateAnnualInterest() {
        return balance.doubleValue() * INTEREST_RATE;
    }
}
