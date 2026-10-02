package com.saurabh.banking;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.account.SavingsAccount;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.customer.Customer;
import com.saurabh.banking.fraud.FraudDetector;
import com.saurabh.banking.service.BankingService;
import com.saurabh.banking.transaction.Transaction;

public class Main {
    public static void main(String[] args) {
        Customer customer = new Customer(1L, "Saurabh Kushwaha",
                "saurabh@example.com", "9999999999");

        Account account = new SavingsAccount(10001L, customer, 10000.0);
        BankingService banking = new BankingService(new AuditLogger());

        banking.deposit(account, 5000);
        banking.withdraw(account, 1500);

        Transaction transaction = banking.transfer(
                account, account, 500, "SELF-TRANSFER");

        FraudDetector detector = new FraudDetector();
        System.out.println("Customer: " + customer.getName());
        System.out.println("Balance: " + account.getBalance());
        System.out.println("Transaction: " + transaction.getTransactionId());
        System.out.println("Risk: " + detector.evaluate(transaction.getAmount()));
    }
}
