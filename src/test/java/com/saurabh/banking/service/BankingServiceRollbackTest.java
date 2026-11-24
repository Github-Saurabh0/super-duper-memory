
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

        assertEquals(
                1,
                exception.getSuppressed().length,
                "Connection close failure should be suppressed");

        IllegalStateException suppressedCloseFailure = (IllegalStateException) exception.getSuppressed()[0];

        assertEquals(
                "Failed to close database connection",
                suppressedCloseFailure.getMessage());

        assertSame(
                closeFailure,
                suppressedCloseFailure.getCause(),
                "Original connection close failure should be preserved");
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
                0,
                exception.getSuppressed().length,
                "No suppressed exception should exist when connection acquisition fails");

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

    @Test
    void rollbackFailureAfterCommitFailureShouldPreserveCommitException() {
        SQLException commitFailure = new SQLException("Simulated commit failure");

        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

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

        ConnectionProvider provider = () -> connection;

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
                "The original commit failure should remain the primary cause");

        assertEquals(
                1,
                commitFailure.getSuppressed().length,
                "The rollback failure should be suppressed on the commit failure");

        assertSame(
                rollbackFailure,
                commitFailure.getSuppressed()[0]);

        assertTrue(rollbackCalled.get(), "Rollback should be attempted");
        assertTrue(closeCalled.get(), "Connection should still be closed");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "The in-memory balance should be restored");
    }

    @Test
    void setAutoCommitFailureShouldRollbackAndRestoreBalance() {
        SQLException autoCommitFailure = new SQLException("Simulated setAutoCommit failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean balanceUpdateCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            throw autoCommitFailure;

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

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {
                balanceUpdateCalled.set(true);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                fail("Transaction should not be saved");
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
                autoCommitFailure,
                exception.getCause(),
                "Original setAutoCommit failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted after setAutoCommit fails");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after failure");

        assertFalse(
                balanceUpdateCalled.get(),
                "Balance update should not be reached");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "In-memory balance should be restored");
    }

    @Test
    void rollbackFailureAfterSetAutoCommitFailureShouldPreserveOriginalException() {
        SQLException autoCommitFailure = new SQLException("Simulated setAutoCommit failure");

        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean balanceUpdateCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            throw autoCommitFailure;

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

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {
                balanceUpdateCalled.set(true);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                fail("Transaction should not be saved");
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
                autoCommitFailure,
                exception.getCause(),
                "Original setAutoCommit failure should be preserved");

        assertArrayEquals(
                new Throwable[] { rollbackFailure },
                autoCommitFailure.getSuppressed(),
                "Rollback failure should be suppressed on the original exception");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after failure");

        assertFalse(
                balanceUpdateCalled.get(),
                "Balance update should not be reached");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "In-memory balance should be restored");

    }

    @Test
    void firstBalanceUpdateFailureShouldRollbackAndRestoreBalance() {
        SQLException updateFailure = new SQLException("Simulated first balance update failure");
        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

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

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) throws SQLException {
                throw updateFailure;
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                transactionSaveCalled.set(true);
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
                updateFailure,
                exception.getCause(),
                "Original balance update failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted after update failure");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after failure");

        assertFalse(
                transactionSaveCalled.get(),
                "Transaction should not be saved after update failure");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "In-memory balance should be restored");
        assertSame(
                updateFailure,
                exception.getCause(),
                "Original balance update failure should be preserved");

        assertEquals(
                1,
                exception.getCause().getSuppressed().length,
                "Rollback failure should be suppressed on the original update failure");

        assertSame(
                rollbackFailure,
                exception.getCause().getSuppressed()[0],
                "Original rollback failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted after update failure");
    }

    @Test
    void transferRollbackFailureShouldPreserveUpdateFailureAndRestoreBalances() {
        SQLException updateFailure = new SQLException("Simulated second account update failure");

        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

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

        SavingsAccount from = new SavingsAccount(100L, customer, 1000);
        SavingsAccount to = new SavingsAccount(200L, customer, 500);

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) throws SQLException {

                if (accountNumber.equals(100L)) {
                    firstUpdateCompleted.set(true);
                    return;
                }

                if (accountNumber.equals(200L)) {
                    throw updateFailure;
                }

                fail("Unexpected account number: " + accountNumber);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                transactionSaveCalled.set(true);
                fail("Transaction should not be saved");
            }
        };

        BankingService service = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository,
                () -> connection);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(from, to, 200, "TRANSFER"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                updateFailure,
                exception.getCause(),
                "Original second update failure should be preserved");

        assertArrayEquals(
                new Throwable[] { rollbackFailure },
                updateFailure.getSuppressed(),
                "Rollback failure should be suppressed on the original exception");

        assertTrue(firstUpdateCompleted.get());
        assertTrue(rollbackCalled.get(), "Rollback should be attempted");
        assertTrue(closeCalled.get(), "Connection should be closed");

        assertFalse(
                transactionSaveCalled.get(),
                "Transaction should not be saved after update failure");

        assertEquals(1000.0, from.getBalance(), 0.001);
        assertEquals(500.0, to.getBalance(), 0.001);
    }

    @Test
    void transactionSaveAndRollbackFailureShouldPreserveOriginalException() {
        SQLException saveFailure = new SQLException("Simulated transaction save failure");

        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean balanceUpdateCalled = new AtomicBoolean(false);

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
                balanceUpdateCalled.set(true);
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

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                saveFailure,
                exception.getCause(),
                "Original transaction save failure should be preserved");

        assertArrayEquals(
                new Throwable[] { rollbackFailure },
                saveFailure.getSuppressed(),
                "Rollback failure should be suppressed on save failure");

        assertTrue(
                balanceUpdateCalled.get(),
                "Account balance update should execute before transaction save");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "In-memory balance should be restored");
    }

    @Test
    void withdrawShouldRollbackAndRestoreBalanceWhenPersistenceFails() {
        SQLException updateFailure = new SQLException("Simulated withdrawal balance update failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

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

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) throws SQLException {
                throw updateFailure;
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                transactionSaveCalled.set(true);
            }
        };

        BankingService service = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.withdraw(account, 200));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                updateFailure,
                exception.getCause(),
                "Original balance update failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted after withdrawal update failure");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after failure");

        assertFalse(
                transactionSaveCalled.get(),
                "Transaction should not be saved after update failure");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "In-memory balance should be restored after withdrawal failure");
    }

    @Test
    void withdrawalCommitFailureShouldRollbackAndRestoreBalance() {
        SQLException commitFailure = new SQLException("Simulated withdrawal commit failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

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

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) throws SQLException {
                // Simulate successful withdrawal balance update before commit.
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                transactionSaveCalled.set(true);
            }
        };

        BankingService service = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.withdraw(account, 200));

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

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction should be saved before commit");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "In-memory balance should be restored after withdrawal commit failure");
    }

    @Test
    void withdrawalTransactionSaveFailureShouldRollbackAndRestoreBalance() {
        SQLException saveFailure = new SQLException("Simulated withdrawal transaction save failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

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
                transactionSaveCalled.set(true);
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
                () -> service.withdraw(account, 200));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                saveFailure,
                exception.getCause(),
                "Original transaction save failure should be preserved");

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction save should be attempted");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be called after transaction save failure");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after failure");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "In-memory balance should be restored after transaction save failure");
    }

    @Test
    void withdrawalRollbackFailureAfterCommitFailureShouldPreserveCommitException() {
        SQLException commitFailure = new SQLException("Simulated withdrawal commit failure");

        SQLException rollbackFailure = new SQLException("Simulated withdrawal rollback failure");

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

        ConnectionProvider provider = () -> connection;

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
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.withdraw(account, 200));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                commitFailure,
                exception.getCause(),
                "The original commit failure should remain the primary cause");

        assertEquals(
                1,
                commitFailure.getSuppressed().length,
                "The rollback failure should be suppressed on the commit failure");

        assertSame(
                rollbackFailure,
                commitFailure.getSuppressed()[0],
                "The original rollback failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted after commit failure");

        assertTrue(
                closeCalled.get(),
                "Connection should still be closed");

        assertEquals(
                1000.0,
                account.getBalance(),
                0.001,
                "The in-memory balance should be restored");
    }

    @Test
    void withdrawalShouldNotRestoreBalanceWhenAuditLoggingFailsAfterCommit() {
        IllegalStateException auditFailure = new IllegalStateException("Simulated audit logging failure");

        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

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

        ConnectionProvider provider = () -> connection;

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
                transactionSaveCalled.set(true);
                assertSame(connection, conn);
            }
        };

        AuditLogger failingAuditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                throw auditFailure;
            }
        };

        BankingService service = new BankingService(
                failingAuditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.withdraw(account, 200));

        assertSame(
                auditFailure,
                exception,
                "Original audit failure should be propagated");

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction should be saved before audit logging");

        assertTrue(
                commitCalled.get(),
                "Database transaction should be committed before audit logging");

        assertFalse(
                rollbackCalled.get(),
                "Rollback should not occur after a successful commit");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after successful commit");

        assertEquals(
                800.0,
                account.getBalance(),
                0.001,
                "Balance should remain changed because database commit succeeded");
    }

    @Test
    void depositShouldNotRestoreBalanceWhenAuditLoggingFailsAfterCommit() {
        IllegalStateException auditFailure = new IllegalStateException("Simulated audit logging failure");

        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount account = new SavingsAccount(
                100L,
                customer,
                1000);

        ConnectionProvider provider = () -> connection;

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

                transactionSaveCalled.set(true);
                assertSame(connection, conn);
            }
        };

        AuditLogger failingAuditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                throw auditFailure;
            }
        };

        BankingService service = new BankingService(
                failingAuditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.deposit(account, 300));

        assertSame(
                auditFailure,
                exception,
                "Original audit failure should be propagated");

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction should be saved before audit logging");

        assertTrue(
                commitCalled.get(),
                "Database transaction should be committed before audit logging");

        assertFalse(
                rollbackCalled.get(),
                "Rollback should not occur after a successful commit");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after successful commit");

        assertEquals(
                1300.0,
                account.getBalance(),
                0.001,
                "Balance should remain changed because database commit succeeded");
    }

    @Test
    void transferShouldNotRestoreBalancesWhenAuditLoggingFailsAfterCommit() {
        IllegalStateException auditFailure = new IllegalStateException("Simulated audit logging failure");

        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

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

                transactionSaveCalled.set(true);
                assertSame(connection, conn);
            }
        };

        AuditLogger failingAuditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                throw auditFailure;
            }
        };

        BankingService service = new BankingService(
                failingAuditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertSame(
                auditFailure,
                exception,
                "Original audit failure should be propagated");

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction should be saved before audit logging");

        assertTrue(
                commitCalled.get(),
                "Database transaction should be committed before audit logging");

        assertFalse(
                rollbackCalled.get(),
                "Rollback should not occur after a successful commit");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed after successful commit");

        assertEquals(
                800.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should remain debited after successful commit");

        assertEquals(
                700.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should remain credited after successful commit");
    }

    @Test
    void transferShouldPreserveBalancesWhenConnectionCloseFailsAfterCommit() {
        SQLException closeFailure = new SQLException("Simulated connection close failure");

        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

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
                            rollbackCalled.set(true);
                            return null;

                        case "close":
                            closeCalled.set(true);
                            throw closeFailure;

                        case "isClosed":
                            return false;

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

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

                transactionSaveCalled.set(true);
                assertSame(connection, conn);
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                // Audit succeeds.
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        assertDoesNotThrow(() -> service.transfer(
                sourceAccount,
                targetAccount,
                200,
                "Test transfer"));

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction should be saved before commit");

        assertTrue(
                commitCalled.get(),
                "Database transaction should be committed");

        assertTrue(
                closeCalled.get(),
                "Connection close should be attempted");

        assertFalse(
                rollbackCalled.get(),
                "Rollback should not occur after successful commit");

        assertEquals(
                800.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should remain debited after successful commit");

        assertEquals(
                700.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should remain credited after successful commit");
    }

    @Test
    void transferCloseFailureBeforeCommitShouldPreserveTransactionFailureAndRestoreBalances() {
        SQLException commitFailure = new SQLException("Simulated commit failure");

        SQLException closeFailure = new SQLException("Simulated connection close failure");

        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;

                        case "commit":
                            commitCalled.set(true);
                            throw commitFailure;

                        case "rollback":
                            rollbackCalled.set(true);
                            return null;

                        case "close":
                            closeCalled.set(true);
                            throw closeFailure;

                        case "isClosed":
                            return false;

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

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

                transactionSaveCalled.set(true);
                assertSame(connection, conn);
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                // Audit should not be reached because commit fails.
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                commitFailure,
                exception.getCause(),
                "Original commit failure should be preserved");

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction should be saved before commit");

        assertTrue(
                commitCalled.get(),
                "Commit should be attempted");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted after commit failure");

        assertTrue(
                closeCalled.get(),
                "Connection close should be attempted");

        assertEquals(
                1,
                exception.getSuppressed().length,
                "Connection close failure should be suppressed");

        IllegalStateException suppressedCloseFailure = (IllegalStateException) exception.getSuppressed()[0];

        assertEquals(
                "Failed to close database connection",
                suppressedCloseFailure.getMessage());

        assertSame(
                closeFailure,
                suppressedCloseFailure.getCause(),
                "Original connection close failure should be preserved");

        assertEquals(
                1000.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should be restored after transaction failure");

        assertEquals(
                500.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should be restored after transaction failure");
    }

    @Test
    void transferConnectionAcquisitionFailureShouldRestoreBalances() {
        SQLException connectionFailure = new SQLException("Simulated connection acquisition failure");

        AtomicBoolean updateBalanceCalled = new AtomicBoolean(false);

        Customer customer = new Customer(
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> {
            throw connectionFailure;
        };

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {

                updateBalanceCalled.set(true);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {
                fail("Transaction should not be saved");
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                fail("Audit should not be called");
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                connectionFailure,
                exception.getCause(),
                "Original connection failure should be preserved");

        assertFalse(
                updateBalanceCalled.get(),
                "Balance update should not happen without a connection");

        assertEquals(
                1000.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should remain unchanged");

        assertEquals(
                500.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should remain unchanged");
    }

    @Test
    void transferTransactionSaveAndRollbackFailureShouldPreserveOriginalExceptionAndRestoreBalances() {
        SQLException saveFailure = new SQLException("Simulated transaction save failure");

        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

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
                            fail("Commit should not be reached");

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

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

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                fail("Audit should not be called");
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                saveFailure,
                exception.getCause(),
                "Original transaction save failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed");

        assertEquals(
                1,
                saveFailure.getSuppressed().length,
                "Rollback failure should be suppressed on original failure");

        assertSame(
                rollbackFailure,
                saveFailure.getSuppressed()[0],
                "Rollback failure should be the suppressed exception");

        assertEquals(
                1000.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should be restored");

        assertEquals(
                500.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should be restored");
    }

    @Test
    void transferCommitRollbackAndCloseFailureShouldPreserveAllExceptionsAndRestoreBalances() {
        SQLException commitFailure = new SQLException("Simulated commit failure");

        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

        SQLException closeFailure = new SQLException("Simulated connection close failure");

        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;

                        case "commit":
                            commitCalled.set(true);
                            throw commitFailure;

                        case "rollback":
                            rollbackCalled.set(true);
                            throw rollbackFailure;

                        case "close":
                            closeCalled.set(true);
                            throw closeFailure;

                        case "isClosed":
                            return false;

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

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

                transactionSaveCalled.set(true);
                assertSame(connection, conn);
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                fail("Audit should not be called when commit fails");
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                commitFailure,
                exception.getCause(),
                "Original commit failure should be preserved");

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction should be saved before commit");

        assertTrue(
                commitCalled.get(),
                "Commit should be attempted");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted after commit failure");

        assertTrue(
                closeCalled.get(),
                "Connection close should be attempted");

        assertEquals(
                1,
                commitFailure.getSuppressed().length,
                "Rollback failure should be suppressed on commit failure");

        assertSame(
                rollbackFailure,
                commitFailure.getSuppressed()[0],
                "Rollback failure should be preserved as suppressed exception");

        assertEquals(
                1,
                exception.getSuppressed().length,
                "Close failure should be suppressed on operation failure");

        IllegalStateException suppressedCloseFailure = (IllegalStateException) exception.getSuppressed()[0];

        assertEquals(
                "Failed to close database connection",
                suppressedCloseFailure.getMessage());

        assertSame(
                closeFailure,
                suppressedCloseFailure.getCause(),
                "Original close failure should be preserved");

        assertEquals(
                1000.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should be restored");

        assertEquals(
                500.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should be restored");
    }

    @Test
    void transferSecondBalanceUpdateFailureShouldRollbackAndRestoreBothBalances() {
        SQLException updateFailure = new SQLException("Simulated second balance update failure");

        AtomicBoolean firstUpdateCalled = new AtomicBoolean(false);
        AtomicBoolean secondUpdateCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;

                        case "commit":
                            fail("Commit should not be reached");

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance)
                    throws SQLException {

                assertSame(connection, conn);

                if (accountNumber.equals(100L)) {
                    firstUpdateCalled.set(true);
                    return;
                }

                if (accountNumber.equals(200L)) {
                    secondUpdateCalled.set(true);
                    throw updateFailure;
                }

                fail("Unexpected account number: " + accountNumber);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {

                transactionSaveCalled.set(true);
                fail("Transaction should not be saved");
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                fail("Audit should not be called");
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                updateFailure,
                exception.getCause(),
                "Original second balance update failure should be preserved");

        assertTrue(
                firstUpdateCalled.get(),
                "First account balance update should be attempted");

        assertTrue(
                secondUpdateCalled.get(),
                "Second account balance update should be attempted");

        assertFalse(
                transactionSaveCalled.get(),
                "Transaction should not be saved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed");

        assertEquals(
                1000.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should be restored");

        assertEquals(
                500.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should be restored");
    }

    @Test
    void transferSecondBalanceUpdateRuntimeFailureShouldRollbackAndRestoreBothBalances() {
        IllegalStateException updateFailure = new IllegalStateException(
                "Simulated second balance update runtime failure");

        AtomicBoolean firstUpdateCalled = new AtomicBoolean(false);
        AtomicBoolean secondUpdateCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);
        AtomicBoolean commitCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            return null;

                        case "commit":
                            commitCalled.set(true);
                            fail("Commit should not be reached");

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {

                assertSame(connection, conn);

                if (accountNumber.equals(100L)) {
                    firstUpdateCalled.set(true);
                    return;
                }

                if (accountNumber.equals(200L)) {
                    secondUpdateCalled.set(true);
                    throw updateFailure;
                }

                fail("Unexpected account number: " + accountNumber);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {

                transactionSaveCalled.set(true);
                fail("Transaction should not be saved");
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                fail("Audit should not be called");
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                updateFailure,
                exception.getCause(),
                "Original runtime failure should be preserved");

        assertTrue(
                firstUpdateCalled.get(),
                "First account balance update should be attempted");

        assertTrue(
                secondUpdateCalled.get(),
                "Second account balance update should be attempted");

        assertFalse(
                transactionSaveCalled.get(),
                "Transaction should not be saved");

        assertFalse(
                commitCalled.get(),
                "Commit should not be reached");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed");

        assertEquals(
                1000.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should be restored");

        assertEquals(
                500.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should be restored");
    }

    @Test
    void transferShouldPreserveBalancesWhenAuditLoggingThrowsRuntimeExceptionAfterCommit() {
        IllegalStateException auditFailure = new IllegalStateException(
                "Simulated audit runtime failure");

        AtomicBoolean firstUpdateCalled = new AtomicBoolean(false);
        AtomicBoolean secondUpdateCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);
        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

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
                            fail("Rollback should not be called after successful commit");

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {

                assertSame(connection, conn);

                if (accountNumber.equals(100L)) {
                    firstUpdateCalled.set(true);
                    return;
                }

                if (accountNumber.equals(200L)) {
                    secondUpdateCalled.set(true);
                    return;
                }

                fail("Unexpected account number: " + accountNumber);
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {

                transactionSaveCalled.set(true);
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                throw auditFailure;
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertSame(
                auditFailure,
                exception,
                "Original audit runtime failure should be propagated");

        assertTrue(
                firstUpdateCalled.get(),
                "First account balance update should be attempted");

        assertTrue(
                secondUpdateCalled.get(),
                "Second account balance update should be attempted");

        assertTrue(
                transactionSaveCalled.get(),
                "Transaction should be saved before commit");

        assertTrue(
                commitCalled.get(),
                "Commit should succeed before audit logging");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed");

        assertEquals(
                800.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should remain debited after commit");

        assertEquals(
                700.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should remain credited after commit");
    }

    @Test
    void transferSetAutoCommitFailureAndRollbackFailureShouldPreserveOriginalExceptionAndRestoreBalances() {
        SQLException autoCommitFailure = new SQLException("Simulated setAutoCommit failure");

        SQLException rollbackFailure = new SQLException("Simulated rollback failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);
        AtomicBoolean commitCalled = new AtomicBoolean(false);
        AtomicBoolean updateCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            throw autoCommitFailure;

                        case "rollback":
                            rollbackCalled.set(true);
                            throw rollbackFailure;

                        case "commit":
                            commitCalled.set(true);
                            fail("Commit should not be reached");

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {

                updateCalled.set(true);

                fail("Balance update should not be reached");
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {

                transactionSaveCalled.set(true);
                fail("Transaction should not be saved");
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                fail("Audit should not be called");
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                autoCommitFailure,
                exception.getCause(),
                "Original setAutoCommit failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted");

        assertEquals(
                1,
                autoCommitFailure.getSuppressed().length,
                "Rollback failure should be suppressed on original exception");

        assertSame(
                rollbackFailure,
                autoCommitFailure.getSuppressed()[0],
                "Rollback failure should be suppressed");

        assertFalse(
                updateCalled.get(),
                "Balance update should not be reached");

        assertFalse(
                transactionSaveCalled.get(),
                "Transaction should not be saved");

        assertFalse(
                commitCalled.get(),
                "Commit should not be reached");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed");

        assertEquals(
                1000.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should remain unchanged");

        assertEquals(
                500.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should remain unchanged");
    }

    @Test
    void transferSetAutoCommitRuntimeFailureShouldRollbackAndRestoreBalances() {
        IllegalStateException autoCommitFailure = new IllegalStateException(
                "Simulated setAutoCommit runtime failure");

        AtomicBoolean rollbackCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        AtomicBoolean updateCalled = new AtomicBoolean(false);
        AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);
        AtomicBoolean commitCalled = new AtomicBoolean(false);

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit":
                            throw autoCommitFailure;

                        case "rollback":
                            rollbackCalled.set(true);
                            return null;

                        case "commit":
                            commitCalled.set(true);
                            fail("Commit should not be reached");

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
                1L,
                "Test User",
                "test@example.com",
                "123");

        SavingsAccount sourceAccount = new SavingsAccount(
                100L,
                customer,
                1000);

        SavingsAccount targetAccount = new SavingsAccount(
                200L,
                customer,
                500);

        ConnectionProvider provider = () -> connection;

        AccountRepository accountRepository = new AccountRepository() {
            @Override
            public void updateBalance(
                    Connection conn,
                    Long accountNumber,
                    java.math.BigDecimal balance) {

                updateCalled.set(true);
                fail("Balance update should not be reached");
            }
        };

        TransactionRepository transactionRepository = new TransactionRepository() {
            @Override
            public void save(
                    Connection conn,
                    com.saurabh.banking.transaction.Transaction tx) {

                transactionSaveCalled.set(true);
                fail("Transaction should not be saved");
            }
        };

        AuditLogger auditLogger = new AuditLogger() {
            @Override
            public void log(String event) {
                fail("Audit should not be called");
            }
        };

        BankingService service = new BankingService(
                auditLogger,
                transactionRepository,
                accountRepository,
                provider);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.transfer(
                        sourceAccount,
                        targetAccount,
                        200,
                        "Test transfer"));

        assertEquals(
                "Database transaction failed",
                exception.getMessage());

        assertSame(
                autoCommitFailure,
                exception.getCause(),
                "Original runtime failure should be preserved");

        assertTrue(
                rollbackCalled.get(),
                "Rollback should be attempted");

        assertTrue(
                closeCalled.get(),
                "Connection should be closed");

        assertFalse(
                updateCalled.get(),
                "Balance update should not be reached");

        assertFalse(
                transactionSaveCalled.get(),
                "Transaction should not be saved");

        assertFalse(
                commitCalled.get(),
                "Commit should not be reached");

        assertEquals(
                1000.0,
                sourceAccount.getBalance(),
                0.001,
                "Source balance should remain unchanged");

        assertEquals(
                500.0,
                targetAccount.getBalance(),
                0.001,
                "Target balance should remain unchanged");
    }

    @Test
void transferFirstBalanceUpdateRuntimeFailureShouldRollbackAndRestoreBalances() {
    IllegalStateException updateFailure =
            new IllegalStateException(
                    "Simulated first balance update runtime failure");

    AtomicBoolean firstUpdateCalled = new AtomicBoolean(false);
    AtomicBoolean secondUpdateCalled = new AtomicBoolean(false);
    AtomicBoolean rollbackCalled = new AtomicBoolean(false);
    AtomicBoolean closeCalled = new AtomicBoolean(false);
    AtomicBoolean transactionSaveCalled = new AtomicBoolean(false);
    AtomicBoolean commitCalled = new AtomicBoolean(false);

    Connection connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setAutoCommit":
                        return null;

                    case "commit":
                        commitCalled.set(true);
                        fail("Commit should not be reached");

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
            1L,
            "Test User",
            "test@example.com",
            "123");

    SavingsAccount sourceAccount = new SavingsAccount(
            100L,
            customer,
            1000);

    SavingsAccount targetAccount = new SavingsAccount(
            200L,
            customer,
            500);

    ConnectionProvider provider = () -> connection;

    AccountRepository accountRepository = new AccountRepository() {
        @Override
        public void updateBalance(
                Connection conn,
                Long accountNumber,
                java.math.BigDecimal balance) {

            assertSame(connection, conn);

            if (accountNumber.equals(100L)) {
                firstUpdateCalled.set(true);
                throw updateFailure;
            }

            if (accountNumber.equals(200L)) {
                secondUpdateCalled.set(true);
                fail("Second balance update should not be reached");
            }

            fail("Unexpected account number: " + accountNumber);
        }
    };

    TransactionRepository transactionRepository =
            new TransactionRepository() {
                @Override
                public void save(
                        Connection conn,
                        com.saurabh.banking.transaction.Transaction tx) {

                    transactionSaveCalled.set(true);
                    fail("Transaction should not be saved");
                }
            };

    AuditLogger auditLogger = new AuditLogger() {
        @Override
        public void log(String event) {
            fail("Audit should not be called");
        }
    };

    BankingService service = new BankingService(
            auditLogger,
            transactionRepository,
            accountRepository,
            provider);

    IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> service.transfer(
                    sourceAccount,
                    targetAccount,
                    200,
                    "Test transfer"));

    assertEquals(
            "Database transaction failed",
            exception.getMessage());

    assertSame(
            updateFailure,
            exception.getCause(),
            "Original runtime failure should be preserved");

    assertTrue(
            firstUpdateCalled.get(),
            "First account balance update should be attempted");

    assertFalse(
            secondUpdateCalled.get(),
            "Second account balance update should not be reached");

    assertFalse(
            transactionSaveCalled.get(),
            "Transaction should not be saved");

    assertFalse(
            commitCalled.get(),
            "Commit should not be reached");

    assertTrue(
            rollbackCalled.get(),
            "Rollback should be attempted");

    assertTrue(
            closeCalled.get(),
            "Connection should be closed");

    assertEquals(
            1000.0,
            sourceAccount.getBalance(),
            0.001,
            "Source balance should remain unchanged");

    assertEquals(
            500.0,
            targetAccount.getBalance(),
            0.001,
            "Target balance should remain unchanged");
}
}
