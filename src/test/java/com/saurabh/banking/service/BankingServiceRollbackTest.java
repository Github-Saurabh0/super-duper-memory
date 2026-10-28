
package com.saurabh.banking.service;

import com.saurabh.banking.account.SavingsAccount;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.customer.Customer;
import com.saurabh.banking.repository.AccountRepository;
import com.saurabh.banking.repository.TransactionRepository;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

import com.saurabh.banking.database.ConnectionProvider;

class BankingServiceRollbackTest {

    @Test
    void failedTransactionSaveShouldRollbackAndRestoreBalance() {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
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

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {
                assertSame(connection, conn);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
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

    @Test
    void transferShouldRollbackAndRestoreBothBalancesWhenSecondUpdateFails() {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);
        AtomicBoolean firstUpdateCompleted = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
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

        SavingsAccount from = new SavingsAccount(100L, customer, 1000);
        SavingsAccount to = new SavingsAccount(200L, customer, 500);

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) throws SQLException {

                assertSame(connection, conn);

                if (accountNumber.equals(100L)) {
                    firstUpdateCompleted.set(true);
                    return;
                }

                if (accountNumber.equals(200L)) {
                    throw new SQLException("Simulated second account update failure");
                }

                fail("Unexpected account number: " + accountNumber);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx)
                    throws SQLException {
                transactionSaveCalled.set(true);
                throw new SQLException("Transaction save should not be reached");
            }
        };

        BankingService service = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository,
                () -> connection);

        assertThrows(
                IllegalStateException.class,
                () -> service.transfer(from, to, 200, "TRANSFER"));

        assertTrue(firstUpdateCompleted.get());
        assertTrue(rollbackCalled.get(), "Rollback should be called");
        assertTrue(closeCalled.get(), "Connection should be closed");
        assertFalse(
                transactionSaveCalled.get(),
                "Transaction must not be saved after the second update fails");

        assertEquals(1000.0, from.getBalance(), 0.001);
        assertEquals(500.0, to.getBalance(), 0.001);
    }

    @Test
    void rollbackFailureShouldBeSuppressedOnOriginalDatabaseFailure() {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        SQLException saveFailure = new SQLException("Simulated transaction save failure");

        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;
                        case "rollback":
                            rollbackCalled.set(true);
                            throw rollbackFailure;
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

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {
                assertSame(connection, conn);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx)
                    throws SQLException {
                throw saveFailure;
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

        assertEquals("Database transaction failed", exception.getMessage());
        assertSame(saveFailure, exception.getCause());

        Throwable originalFailure = exception.getCause();

        assertNotNull(originalFailure);
        assertSame(saveFailure, originalFailure);

        assertEquals(1, originalFailure.getSuppressed().length);
        assertSame(rollbackFailure, originalFailure.getSuppressed()[0]);

        assertTrue(rollbackCalled.get(), "Rollback should be attempted");
        assertTrue(closeCalled.get(), "Connection should still be closed");
        assertEquals(1000.0, account.getBalance(), 0.001);
    }

    @Test
    void connectionCloseFailureAfterCommitShouldPreserveBalance() {
        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        SQLException closeFailure = new SQLException("Simulated connection close failure");

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;

                        case "commit":
                            commitCalled.set(true);
                            return null;

                        case "rollback":
                            return null;

                        case "close":
                            closeCalled.set(true);
                            throw closeFailure;

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

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {
                assertSame(connection, conn);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                assertSame(connection, conn);
            }
        };

        BankingService service = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository,
                () -> connection);

        assertDoesNotThrow(() -> service.deposit(account, 200));

        assertTrue(commitCalled.get(), "Transaction should commit");
        assertTrue(closeCalled.get(), "Connection close should be attempted");

        assertEquals(
                1200.0,
                account.getBalance(),
                0.001,
                "Balance should remain updated after successful commit");
    }

    @Test
    void connectionCloseFailureBeforeCommitShouldPreserveTransactionFailure() {
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        SQLException saveFailure = new SQLException("Simulated transaction save failure");

        SQLException closeFailure = new SQLException("Simulated connection close failure");

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;

                        case "rollback":
                            rollbackCalled.set(true);
                            return null;

                        case "close":
                            closeCalled.set(true);
                            throw closeFailure;

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

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {
                assertSame(connection, conn);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx)
                    throws SQLException {
                assertSame(connection, conn);
                throw saveFailure;
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

        assertTrue(rollbackCalled.get(), "Rollback should be attempted");
        assertTrue(closeCalled.get(), "Connection close should be attempted");

        assertEquals(1000.0, account.getBalance(), 0.001);

        // The original transaction failure should remain the primary cause.
        assertSame(
                saveFailure,
                exception.getCause(),
                "Original transaction failure should be preserved");
    }

    @Test
    void connectionAcquisitionFailureShouldRestoreBalance() {
        SQLException connectionFailure = new SQLException("Simulated connection acquisition failure");

        Customer customer = new Customer(
                1L, "Test User", "test@example.com", "123");

        SavingsAccount account = new SavingsAccount(
                100L, customer, 1000);

        ConnectionProvider failingProvider = () -> {
            throw connectionFailure;
        };

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {
                fail("Account update should not be reached");
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                fail("Transaction save should not be reached");
            }
        };

        BankingService service = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository,
                failingProvider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.deposit(account, 200));

        assertEquals("Database transaction failed", exception.getMessage());
        assertSame(
                connectionFailure,
                exception.getCause(),
                "Original connection failure should be preserved");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "Balance should be restored after connection acquisition fails");
    }

    @Test
    void commitFailureShouldRollbackAndRestoreBalance() {
        SQLException commitFailure = new SQLException("Simulated commit failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;

                        case "commit":
                            throw commitFailure;

                        case "rollback":
                            rollbackCalled.set(true);
                            return null;

                        case "close":
                            closeCalled.set(true);
                            return null;

                        case "isClosed":
                            return closeCalled.get();

                        default:
                            Class<?> returnType = method.getReturnType();

                            if (returnType == boolean.class) {
                                return false;
                            }
                            if (returnType == int.class) {
                                return 0;
                            }
                            if (returnType == long.class) {
                                return 0L;
                            }

                            return null;
                    }
                });

        Customer customer = new Customer(
                1L, "Test User", "test@example.com", "123");

        SavingsAccount account = new SavingsAccount(
                100L, customer, 1000);

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {
                // Simulate a successful balance update before commit.
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                // Simulate a successful transaction save before commit.
            }
        };

        BankingService service = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.deposit(account, 200));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                commitFailure,
                exception.getCause(),
                "Original commit failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted after commit failure");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after commit failure");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "Balance should be restored after commit failure");
    }

}
