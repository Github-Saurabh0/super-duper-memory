
package com.saurabh.banking.fraud;

public class FraudDetector {

    public String evaluate(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException(
                    "Amount must be finite and non-negative"
            );
        }

        if (amount >= 500_000) {
            return "HIGH_RISK";
        }

        if (amount >= 100_000) {
            return "MEDIUM_RISK";
        }

        return "APPROVED";
    }
}
