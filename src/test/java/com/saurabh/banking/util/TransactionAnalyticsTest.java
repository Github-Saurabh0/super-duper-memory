
package com.saurabh.banking.util;

import com.saurabh.banking.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransactionAnalyticsTest {

    @Test
    void totalValueReturnsSumOfTransactionAmounts() {
        List<Transaction> transactions = List.of(
                Transaction.create(100L, 200L, 500.0, "TRANSFER"),
                Transaction.create(100L, 200L, 1500.0, "TRANSFER"),
                Transaction.create(100L, 200L, 250.0, "TRANSFER")
        );

        assertEquals(2250.0,
                TransactionAnalytics.totalValue(transactions),
                0.001);
    }

    @Test
    void totalValueReturnsZeroForEmptyList() {
        assertEquals(0.0,
                TransactionAnalytics.totalValue(List.of()),
                0.001);
    }

    @Test
    void countByTypeCountsEachTransactionType() {
        List<Transaction> transactions = List.of(
                Transaction.create(100L, 200L, 500.0, "TRANSFER"),
                Transaction.create(100L, 200L, 1000.0, "TRANSFER"),
                Transaction.create(100L, null, 300.0, "DEPOSIT")
        );

        Map<String, Long> counts =
                TransactionAnalytics.countByType(transactions);

        assertEquals(2L, counts.get("TRANSFER"));
        assertEquals(1L, counts.get("DEPOSIT"));
    }

    @Test
    void highValueFiltersAndSortsTransactionsDescending() {
        List<Transaction> transactions = List.of(
                Transaction.create(100L, 200L, 500.0, "TRANSFER"),
                Transaction.create(100L, 200L, 5000.0, "TRANSFER"),
                Transaction.create(100L, 200L, 1500.0, "TRANSFER")
        );

        List<Transaction> result =
                TransactionAnalytics.highValue(transactions, 1000.0);

        assertEquals(2, result.size());
        assertEquals(5000.0, result.get(0).amount(), 0.001);
        assertEquals(1500.0, result.get(1).amount(), 0.001);
    }

    @Test
    void highValueIncludesTransactionAtExactThreshold() {
        List<Transaction> transactions = List.of(
                Transaction.create(100L, 200L, 1000.0, "TRANSFER")
        );

        List<Transaction> result =
                TransactionAnalytics.highValue(transactions, 1000.0);

        assertEquals(1, result.size());
        assertEquals(1000.0, result.get(0).amount(), 0.001);
    }
}
