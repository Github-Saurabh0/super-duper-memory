package com.saurabh.banking.transaction;

import com.saurabh.banking.exception.InvalidTransactionException;

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

    public Transaction {
        if (transactionId == null || transactionId.isBlank()) {
            throw new InvalidTransactionException(
                    "Transaction ID cannot be empty"
            );
        }

        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new InvalidTransactionException(
                    "Transaction amount must be a valid positive value"
            );
        }

        if (type == null || type.isBlank()) {
            throw new InvalidTransactionException(
                    "Transaction type is required"
            );
        }

        if (timestamp == null) {
            throw new InvalidTransactionException(
                    "Transaction timestamp is required"
            );
        }
    }

    public static Transaction create(
            Long from,
            Long to,
            double amount,
            String type
    ) {

        if (amount <= 0) {
            throw new InvalidTransactionException(
                    "Transaction amount must be greater than zero"
            );
        }

        if (type == null || type.isBlank()) {
            throw new InvalidTransactionException(
                    "Transaction type is required"
            );
        }

        return new Transaction(
                UUID.randomUUID().toString(),
                from,
                to,
                amount,
                type,
                LocalDateTime.now()
        );
    }
}