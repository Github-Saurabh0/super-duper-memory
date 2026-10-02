package com.saurabh.banking.transaction;

import java.time.LocalDateTime;
import java.util.UUID;

public record Transaction(
        String transactionId,
        Long fromAccount,
        Long toAccount,
        double amount,
        String type,
        LocalDateTime timestamp
) {
    public static Transaction create(Long from, Long to, double amount, String type) {
        return new Transaction(UUID.randomUUID().toString(), from, to,
                amount, type, LocalDateTime.now());
    }
}
