package com.saurabh.banking;

import com.saurabh.banking.account.SavingsAccount;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.customer.Customer;
import com.saurabh.banking.service.BankingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BankingServiceTest {

    @Test
    void depositShouldIncreaseBalance() {
        Customer c = new Customer(1L, "Test User", "test@example.com", "123");
        SavingsAccount account = new SavingsAccount(100L, c, 1000);
        new BankingService(new AuditLogger()).deposit(account, 500);
        assertEquals(1500, account.getBalance());
    }

    @Test
    void withdrawalShouldReduceBalance() {
        Customer c = new Customer(1L, "Test User", "test@example.com", "123");
        SavingsAccount account = new SavingsAccount(100L, c, 1000);
        new BankingService(new AuditLogger()).withdraw(account, 250);
        assertEquals(750, account.getBalance());
    }
}
