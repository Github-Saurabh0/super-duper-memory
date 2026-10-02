package com.saurabh.banking.account;

import com.saurabh.banking.customer.Customer;
import java.math.BigDecimal;

public class CurrentAccount extends Account {
    private static final double OVERDRAFT_LIMIT = 25000.0;

    public CurrentAccount(Long accountNumber, Customer owner, double openingBalance) {
        super(accountNumber, owner, openingBalance);
    }

    @Override
    public void withdraw(double amount) {
        validateAmount(amount);
        lock.lock();
        try {
            BigDecimal requested = BigDecimal.valueOf(amount);
            BigDecimal minimum = BigDecimal.valueOf(-OVERDRAFT_LIMIT);
            if (balance.subtract(requested).compareTo(minimum) < 0) {
                throw new IllegalStateException("Overdraft limit exceeded");
            }
            balance = balance.subtract(requested);
        } finally {
            lock.unlock();
        }
    }
}
