
package com.saurabh.banking.repository;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.account.CurrentAccount;
import com.saurabh.banking.account.SavingsAccount;
import com.saurabh.banking.customer.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountRepositoryTest {

    private AccountRepository accountRepository;
    private CustomerRepository customerRepository;
    private Customer testCustomer;

    private static final long CUSTOMER_ID = 91001L;
    private static final long SAVINGS_ACCOUNT_NUMBER = 91001L;
    private static final long CURRENT_ACCOUNT_NUMBER = 91002L;

    @BeforeEach
    void setUp() {
        customerRepository = new CustomerRepository();
        accountRepository = new AccountRepository(customerRepository);

        // Clean up records from previous runs.
        accountRepository.delete(SAVINGS_ACCOUNT_NUMBER);
        accountRepository.delete(CURRENT_ACCOUNT_NUMBER);
        customerRepository.delete(CUSTOMER_ID);

        testCustomer = new Customer(
                CUSTOMER_ID,
                "Account Test Customer",
                "account.test91001@example.com",
                "9876500001"
        );

        customerRepository.save(testCustomer);
    }

    @Test
    void shouldSaveAndFindSavingsAccount() {

        Account account = new SavingsAccount(
                SAVINGS_ACCOUNT_NUMBER,
                testCustomer,
                5000.00
        );

        accountRepository.save(account);

        Account result =
                accountRepository.findById(SAVINGS_ACCOUNT_NUMBER);

        assertNotNull(result);
        assertInstanceOf(SavingsAccount.class, result);
        assertEquals(
                SAVINGS_ACCOUNT_NUMBER,
                result.getAccountNumber()
        );
        assertEquals(CUSTOMER_ID, result.getOwner().getId());
        assertEquals(5000.00, result.getBalance(), 0.001);
    }

    @Test
    void shouldSaveAndFindCurrentAccount() {

        Account account = new CurrentAccount(
                CURRENT_ACCOUNT_NUMBER,
                testCustomer,
                10000.00
        );

        accountRepository.save(account);

        Account result =
                accountRepository.findById(CURRENT_ACCOUNT_NUMBER);

        assertNotNull(result);
        assertInstanceOf(CurrentAccount.class, result);
        assertEquals(10000.00, result.getBalance(), 0.001);
    }

    @Test
    void shouldFindAllAccounts() {

        accountRepository.save(
                new SavingsAccount(
                        SAVINGS_ACCOUNT_NUMBER,
                        testCustomer,
                        5000.00
                )
        );

        accountRepository.save(
                new CurrentAccount(
                        CURRENT_ACCOUNT_NUMBER,
                        testCustomer,
                        10000.00
                )
        );

        List<Account> accounts = accountRepository.findAll();

        assertTrue(
                accounts.stream().anyMatch(
                        account -> account.getAccountNumber()
                                .equals(SAVINGS_ACCOUNT_NUMBER)
                )
        );

        assertTrue(
                accounts.stream().anyMatch(
                        account -> account.getAccountNumber()
                                .equals(CURRENT_ACCOUNT_NUMBER)
                )
        );
    }

    @Test
    void shouldDeleteAccount() {

        accountRepository.save(
                new SavingsAccount(
                        SAVINGS_ACCOUNT_NUMBER,
                        testCustomer,
                        5000.00
                )
        );

        assertNotNull(
                accountRepository.findById(SAVINGS_ACCOUNT_NUMBER)
        );

        accountRepository.delete(SAVINGS_ACCOUNT_NUMBER);

        assertNull(
                accountRepository.findById(SAVINGS_ACCOUNT_NUMBER)
        );
    }

    @Test
    void shouldRejectNullAccount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> accountRepository.save(null)
        );
    }
}
