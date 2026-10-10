
package com.saurabh.banking.repository;

import com.saurabh.banking.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRepositoryTest {

    private TransactionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new TransactionRepository();
    }

    @Test
    void shouldSaveAndFindTransaction() {
        Transaction transaction = Transaction.create(
                null, null, 1500.00, "DEPOSIT"
        );

        repository.save(transaction);

        Transaction result =
                repository.findById(transaction.transactionId());

        assertNotNull(result);
        assertEquals(
                transaction.transactionId(),
                result.transactionId()
        );
        assertEquals(1500.00, result.amount(), 0.001);
        assertEquals("DEPOSIT", result.type());

        repository.delete(transaction.transactionId());
    }

    @Test
    void shouldFindAllTransactions() {
        Transaction transaction = Transaction.create(
                null, null, 2500.00, "DEPOSIT"
        );

        repository.save(transaction);

        try {
            List<Transaction> transactions = repository.findAll();

            assertTrue(
                    transactions.stream().anyMatch(
                            item -> item.transactionId().equals(
                                    transaction.transactionId()
                            )
                    )
            );
        } finally {
            repository.delete(transaction.transactionId());
        }
    }

    @Test
    void shouldDeleteTransaction() {
        Transaction transaction = Transaction.create(
                null, null, 500.00, "WITHDRAWAL"
        );

        repository.save(transaction);
        repository.delete(transaction.transactionId());

        assertNull(
                repository.findById(transaction.transactionId())
        );
    }

    @Test
    void shouldRejectNullTransaction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(null)
        );
    }

    @Test
    void shouldRejectBlankTransactionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findById(" ")
        );
    }
}
