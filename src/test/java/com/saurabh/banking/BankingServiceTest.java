package com.saurabh.banking;


import com.saurabh.banking.exception.InvalidTransactionException;
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


    @Test
void depositWithNegativeAmountShouldFail() {

    Customer customer =
            new Customer(
                    1L,
                    "Test User",
                    "test@example.com",
                    "123"
            );

    SavingsAccount account =
            new SavingsAccount(
                    100L,
                    customer,
                    1000
            );

    BankingService banking =
            new BankingService(
                    new AuditLogger()
            );

    assertThrows(
            InvalidTransactionException.class,
            () -> banking.deposit(account, -100)
    );
}



@Test
void withdrawalWithNegativeAmountShouldFail() {

    Customer customer =
            new Customer(
                    1L,
                    "Test User",
                    "test@example.com",
                    "123"
            );

    SavingsAccount account =
            new SavingsAccount(
                    100L,
                    customer,
                    1000
            );

    BankingService banking =
            new BankingService(
                    new AuditLogger()
            );

    assertThrows(
            InvalidTransactionException.class,
            () -> banking.withdraw(account, -100)
    );
}

@Test
void sameAccountTransferShouldFail() {

    Customer customer =
            new Customer(
                    1L,
                    "Test User",
                    "test@example.com",
                    "123"
            );

    SavingsAccount account =
            new SavingsAccount(
                    100L,
                    customer,
                    1000
            );

    BankingService banking =
            new BankingService(
                    new AuditLogger()
            );

    assertThrows(
            InvalidTransactionException.class,
            () -> banking.transfer(
                    account,
                    account,
                    500,
                    "TRANSFER"
            )
    );
}


@Test
void zeroTransferShouldFail() {

    Customer customer =
            new Customer(
                    1L,
                    "Test User",
                    "test@example.com",
                    "123"
            );

    SavingsAccount from =
            new SavingsAccount(
                    100L,
                    customer,
                    1000
            );

    SavingsAccount to =
            new SavingsAccount(
                    200L,
                    customer,
                    500
            );

    BankingService banking =
            new BankingService(
                    new AuditLogger()
            );

    assertThrows(
            InvalidTransactionException.class,
            () -> banking.transfer(
                    from,
                    to,
                    0,
                    "TRANSFER"
            )
    );
}

}
