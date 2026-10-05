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

        // Create customer
        Customer customer = new Customer(
                1L,
                "Saurabh Kushwaha",
                "saurabh@example.com",
                "9999999999"
        );

        // Create source account
        Account account = new SavingsAccount(
                10001L,
                customer,
                10000.0
        );

        // Create destination account
        Account destinationAccount = new SavingsAccount(
                10002L,
                customer,
                5000.0
        );

        // Create banking service
        BankingService banking =
                new BankingService(new AuditLogger());

        // Deposit
        banking.deposit(account, 5000);

        // Withdrawal
        banking.withdraw(account, 1500);

        // Transfer between two different accounts
        Transaction transaction = banking.transfer(
                account,
                destinationAccount,
                500,
                "TRANSFER"
        );

        // Fraud detection
        FraudDetector detector = new FraudDetector();

        // Display customer information
        System.out.println("=================================");
        System.out.println("   ENTERPRISE BANKING SYSTEM");
        System.out.println("=================================");

        System.out.println(
                "Customer: " + customer.getName()
        );

        System.out.println(
                "Source Account Balance: " +
                        account.getBalance()
        );

        System.out.println(
                "Destination Account Balance: " +
                        destinationAccount.getBalance()
        );

        System.out.println(
                "Transaction ID: " +
                        transaction.transactionId()
        );

        System.out.println(
                "Transaction Amount: " +
                        transaction.amount()
        );

        System.out.println(
                "Transaction Type: " +
                        transaction.type()
        );

        System.out.println(
                "Risk: " +
                        detector.evaluate(transaction.amount())
        );

        System.out.println("=================================");
    }
}