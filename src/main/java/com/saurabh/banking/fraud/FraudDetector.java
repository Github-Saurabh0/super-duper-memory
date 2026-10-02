package com.saurabh.banking.fraud;

public class FraudDetector {
    public String evaluate(double amount) {
        if (amount >= 500000) return "HIGH_RISK";
        if (amount >= 100000) return "MEDIUM_RISK";
        return "APPROVED";
    }
}
