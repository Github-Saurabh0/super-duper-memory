
package com.saurabh.banking.service;

import com.saurabh.banking.account.SavingsAccount;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.customer.Customer;
import com.saurabh.banking.repository.AccountRepository;
import com.saurabh.banking.repository.TransactionRepository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BankingServiceRollbackTest {

    @Test
    void failedTransactionSaveShouldRollbackAndRestoreBalance() {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;
                        case "commit":
                            return null;
                        case "rollback":
                            rollbackCalled.set(true);
                            return null;
                        case "close":
                            closeCalled.set(true);
                            return null;
                        case "isClosed":
                            return closeCalled.get();
                        case "isWrapperFor":
                            return false;
                        case "unwrap":
                            throw new SQLException("Not a wrapper");
                        case "toString":
                            return "TestConnection";
                        default:
                            throw new UnsupportedOperationException(
                                    "Unexpected Connection call: "
                                            + method.getName());
                    }
                });

        Customer customer = new Customer(
                1L, "Test User", "test@example.com", "123");

        SavingsAccount account = new SavingsAccount(
                100L, customer, 1000);

        AccountRepository accountRepository =
                new AccountRepository() {
                    @Override
                    public void updateBalance(
                            Connection conn,
                            Long accountNumber,
                            java.math.BigDecimal balance) {
                        assertSame(connection, conn);
                    }
                };

        TransactionRepository transactionRepository =
                new TransactionRepository() {
                    @Override
                    public void save(
                            Connection conn,
                            com.saurabh.banking.transaction.Transaction tx)
                            throws SQLException {
                        throw new SQLException("Simulated transaction save failure");
                    }
                };

        BankingService service = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository,
                () -> connection);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.deposit(account, 200));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertEquals(1000.0, account.getBalance(), 0.001);
        assertTrue(rollbackCalled.get(), "Rollback should be called");
        assertTrue(closeCalled.get(), "Connection should be closed");
    }
}
