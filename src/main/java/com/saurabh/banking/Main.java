
package com.saurabh.banking;

import com.saurabh.banking.account.Account;
import com.saurabh.banking.account.SavingsAccount;
import com.saurabh.banking.audit.AuditLogger;
import com.saurabh.banking.customer.Customer;
import com.saurabh.banking.fraud.FraudDetector;
import com.saurabh.banking.repository.FraudAlertRepository;
import com.saurabh.banking.repository.TransactionRepository;
import com.saurabh.banking.service.BankingService;
import com.saurabh.banking.service.FraudAnalyticsService;
import com.saurabh.banking.transaction.Transaction;
import com.saurabh.banking.util.TransactionAnalytics;
import com.saurabh.banking.repository.AccountRepository;

import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        Customer customer = new Customer(
                1L,
                "Saurabh Kushwaha",
                "saurabh@example.com",
                "9999999999"
        );

        Account account = new SavingsAccount(
                10001L,
                customer,
                10000.0
        );

        Account destinationAccount = new SavingsAccount(
                10002L,
                customer,
                5000.0
        );


        // Initialize repositories and banking service
        AccountRepository accountRepository =
                new AccountRepository();

        TransactionRepository transactionRepository =
                new TransactionRepository();

        BankingService banking = new BankingService(
                new AuditLogger(),
                transactionRepository,
                accountRepository
        );



        // Perform banking operations
        banking.deposit(account, 5000);
        banking.withdraw(account, 1500);

        Transaction transaction = banking.transfer(
                account,
                destinationAccount,
                500,
                "TRANSFER"
        );

        // Fetch actual persisted transaction history
        List<Transaction> transactions =
                transactionRepository.findAll();

        // Fraud detection and alert persistence
        FraudDetector detector = new FraudDetector();
        FraudAlertRepository fraudAlertRepository =
                new FraudAlertRepository();

        String riskLevel = detector.evaluate(transaction.amount());

        if ("HIGH_RISK".equals(riskLevel)
                || "MEDIUM_RISK".equals(riskLevel)) {
            fraudAlertRepository.save(
                    transaction.transactionId(),
                    riskLevel
            );
        }

        // Display banking information
        System.out.println("=================================");
        System.out.println("   ENTERPRISE BANKING SYSTEM");
        System.out.println("=================================");
        System.out.println("Customer: " + customer.getName());
        System.out.println(
                "Source Account Balance: " + account.getBalance()
        );
        System.out.println(
                "Destination Account Balance: "
                        + destinationAccount.getBalance()
        );
        System.out.println(
                "Transaction ID: " + transaction.transactionId()
        );
        System.out.println(
                "Transaction Amount: " + transaction.amount()
        );
        System.out.println(
                "Transaction Type: " + transaction.type()
        );
        System.out.println("Risk: " + riskLevel);

        // Transaction analytics report
        double totalValue =
                TransactionAnalytics.totalValue(transactions);

        Map<String, Long> counts =
                TransactionAnalytics.countByType(transactions);

        List<Transaction> highValueTransactions =
                TransactionAnalytics.highValue(transactions, 1000);

        System.out.println("---------------------------------");
        System.out.println("    TRANSACTION ANALYTICS REPORT");
        System.out.println("---------------------------------");
        System.out.printf(
                "Total Transaction Value: %.2f%n",
                totalValue
        );
        System.out.println("Transaction Count by Type: " + counts);
        System.out.println("High-Value Transactions (>= 1000):");

        if (highValueTransactions.isEmpty()) {
            System.out.println("No high-value transactions found.");
        } else {
            for (Transaction tx : highValueTransactions) {
                System.out.printf(
                        "Type: %s | Amount: %.2f | ID: %s%n",
                        tx.type(),
                        tx.amount(),
                        tx.transactionId()
                );
            }
        }

        // Fraud analytics report
        FraudAnalyticsService analyticsService =
                new FraudAnalyticsService(fraudAlertRepository);

        Map<String, Long> report =
                analyticsService.generateReport();

        System.out.println("---------------------------------");
        System.out.println("       FRAUD ANALYTICS REPORT");
        System.out.println("---------------------------------");
        System.out.println(
                "High Risk Alerts: " + report.get("HIGH_RISK")
        );
        System.out.println(
                "Medium Risk Alerts: " + report.get("MEDIUM_RISK")
        );
        System.out.println(
                "Total Fraud Alerts: " + report.get("TOTAL_ALERTS")
        );
        System.out.println("=================================");
    }
}
