package com.saurabh.banking.util;

import com.saurabh.banking.transaction.Transaction;
import java.util.*;
import java.util.stream.Collectors;

public final class TransactionAnalytics {
    private TransactionAnalytics() {}

    public static double totalValue(List<Transaction> transactions) {
        return transactions.stream()
                .mapToDouble(Transaction::amount)
                .sum();
    }

    public static Map<String, Long> countByType(List<Transaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(Transaction::type, Collectors.counting()));
    }

    public static List<Transaction> highValue(List<Transaction> transactions, double threshold) {
        return transactions.stream()
                .filter(t -> t.amount() >= threshold)
                .sorted(Comparator.comparing(Transaction::amount).reversed())
                .toList();
    }
}
