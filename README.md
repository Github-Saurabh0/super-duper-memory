# Enterprise Banking System - Core Java

> An advanced enterprise-style banking application built with pure Core Java, JDBC, MySQL, concurrency, design patterns, fraud detection, audit logging, and automated testing - without Spring Boot.

![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)
![Maven](https://img.shields.io/badge/Maven-Build-red?logo=apachemaven)
![MySQL](https://img.shields.io/badge/MySQL-8%2B-blue?logo=mysql)
![JUnit 5](https://img.shields.io/badge/JUnit-5-green?logo=junit5)
![JDBC](https://img.shields.io/badge/JDBC-Persistence-purple)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

## Overview

Enterprise Banking System is a production-oriented Core Java project designed to demonstrate how a real-world banking backend can be structured using Java fundamentals.

The project intentionally avoids Spring Boot so that OOP, exception handling, collections, generics, JDBC, transactions, concurrency, design patterns, validation, testing, and data processing are implemented directly.

## Core Objectives

- Build a modular banking domain
- Apply enterprise-level Java architecture
- Implement reliable financial transactions
- Demonstrate thread-safe operations
- Introduce JDBC-based persistence
- Implement fraud and risk evaluation
- Maintain transaction and audit history
- Apply reusable design patterns
- Practice clean, testable Java code

## Architecture

```text
                    ┌─────────────────────────┐
                    │       Main / Client     │
                    └────────────┬────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │    Banking Service      │
                    │ Deposit / Withdraw      │
                    │ Transfer / Validation   │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
       ┌─────────────┐    ┌─────────────┐   ┌─────────────┐
       │   Account   │    │ Transaction │   │    Fraud    │
       │   Domain    │    │   Domain    │   │  Detection  │
       └─────────────┘    └─────────────┘   └─────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │      Repository Layer   │
                    │     Generic + JDBC      │
                    └────────────┬────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │         MySQL           │
                    │ Customers / Accounts    │
                    │ Transactions / Audits   │
                    └─────────────────────────┘
```

## Technology Stack

| Technology | Purpose |
|---|---|
| **Java 17+** | Core application development |
| **Maven** | Build & dependency management |
| **JDBC** | Database connectivity |
| **MySQL** | Persistent storage |
| **JUnit 5** | Unit testing |
| **Java Collections** | Domain data management |
| **Generics** | Type-safe reusable components |
| **Streams & Lambdas** | Analytics and data processing |
| **Concurrency API** | Thread-safe transaction processing |
| **Design Patterns** | Maintainable enterprise architecture |

## Core Modules

### Customer Management

- Customer registration
- Customer information management
- Customer-account relationship
- Validation of customer data

### Account Management

```text
Account
 ├── SavingsAccount
 └── CurrentAccount
```

Features include account creation, balance management, deposits, withdrawals, validation, and account-specific behavior.

### Transaction Management

Supports deposits, withdrawals, fund transfers, transaction validation, unique transaction IDs, timestamps, transaction types, and invalid transaction handling.

```text
Request
   │
   ▼
Validate
   │
   ▼
Lock Accounts
   │
   ▼
Debit Source
   │
   ▼
Credit Destination
   │
   ▼
Create Transaction
   │
   ▼
Audit
   │
   ▼
Return Result
```

## Fraud Detection

The system contains a dedicated fraud and risk evaluation layer.

```text
Transaction
     │
     ▼
Fraud Detector
     │
     ├── Amount Analysis
     ├── Risk Evaluation
     └── Risk Classification
              │
              ▼
        LOW / MEDIUM / HIGH
```

The fraud module is designed so additional rules can be introduced without tightly coupling them to the banking service.

## Audit Logging

The audit layer provides traceability for important banking operations:

```text
DEPOSIT
WITHDRAW
TRANSFER
ACCOUNT_OPERATION
FRAUD_EVENT
```

This provides a foundation for debugging, compliance, transaction tracing, operational monitoring, and security analysis.

## Concurrency & Thread Safety

The project demonstrates Java concurrency concepts for banking operations:

- Thread-safe transaction processing
- Account-level locking
- Concurrent transaction execution
- Deadlock prevention through consistent lock ordering
- Transaction processing queues
- Safe balance updates

```text
Account A ──┐
            ├──► Ordered Locking ──► Transfer
Account B ──┘
```

## JDBC Persistence

The persistence layer is designed around JDBC rather than an ORM.

```text
Application
     │
     ▼
Repository
     │
     ▼
JDBC
     │
     ▼
MySQL
```

The repository architecture uses generics to encourage reusable data-access components:

```java
Repository<ID, Entity>
```

Persistence areas include customers, accounts, transactions, and audit records.

## Generic Repository Architecture

```java
public interface Repository<ID, T> {

    T save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    void deleteById(ID id);
}
```

## Design Patterns

The architecture is designed to demonstrate:

- **Repository Pattern**
- **Factory Pattern**
- **Strategy Pattern**
- **Builder Pattern**
- **Service Layer Pattern**

## Java Concepts Demonstrated

### Object-Oriented Programming

- Encapsulation
- Inheritance
- Polymorphism
- Abstraction
- Composition

### Collections

- `List`
- `Set`
- `Map`
- Queue-based processing
- Collection-based analytics

### Generics

- Generic repositories
- Type-safe services
- Reusable components

### Functional Programming

- Lambda expressions
- Stream API
- Functional interfaces
- Filtering
- Mapping
- Aggregation
- Sorting

### Exception Handling

```java
throw new InvalidTransactionException(
        "Transaction amount must be greater than zero"
);
```

## Testing

Testing is implemented using **JUnit 5**.

```bash
mvn test
```

The test suite covers deposits, withdrawals, transfers, invalid transaction amounts, account validation, transaction validation, and business-rule enforcement.

## Project Structure

```text
enterprise-banking-system/
├── src/
│   ├── main/java/com/saurabh/banking/
│   │   ├── Main.java
│   │   ├── account/
│   │   ├── customer/
│   │   ├── transaction/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── fraud/
│   │   ├── audit/
│   │   ├── concurrency/
│   │   ├── util/
│   │   └── exception/
│   └── test/java/com/saurabh/banking/
│       └── BankingServiceTest.java
├── database/schema.sql
├── docs/
│   ├── architecture.md
│   ├── concurrency.md
│   └── design-patterns.md
├── pom.xml
├── README.md
└── .gitignore
```

## Getting Started

### Clone

```bash
git clone https://github.com/Github-Saurabh0/super-duper-memory.git
cd super-duper-memory
```

### Build

```bash
mvn clean package
```

### Test

```bash
mvn test
```

### Run

```bash
java -cp target/classes com.saurabh.banking.Main
```

### JDBC Configuration

Configure MySQL credentials in `database.properties` or through environment variables before enabling database persistence.

## Build Lifecycle

```text
Developer
    │
    ▼
Source Code
    │
    ▼
Maven
    │
    ├── Compile
    ├── Test
    ├── Package
    │
    ▼
target/
    │
    ▼
Executable Application
```

Run complete verification:

```bash
mvn clean test
```

## Example

```java
Customer customer = new Customer(
        1L,
        "Saurabh Kushwaha",
        "saurabh@example.com",
        "9999999999"
);

Account source = new SavingsAccount(10001L, customer, 10000.0);
Account destination = new SavingsAccount(10002L, customer, 5000.0);

BankingService banking =
        new BankingService(new AuditLogger());

banking.deposit(source, 5000);
banking.withdraw(source, 1500);

Transaction transaction =
        banking.transfer(
                source,
                destination,
                500,
                "ACCOUNT-TRANSFER"
        );
```

## Engineering Principles

- Separation of concerns
- Single responsibility
- Encapsulation
- Fail-fast validation
- Thread safety
- Reusable abstractions
- Testability
- Database abstraction
- Explicit transaction boundaries
- Clean domain modeling

## Project Roadmap

### Completed / In Progress

- [x] Core banking domain
- [x] Customer management
- [x] Savings account
- [x] Current account
- [x] Deposit operations
- [x] Withdrawal operations
- [x] Fund transfer
- [x] Transaction model
- [x] Transaction validation
- [x] Custom transaction exceptions
- [x] Audit logging foundation
- [x] Fraud detection foundation
- [x] Concurrency foundation
- [x] Generic repository abstraction
- [x] JUnit 5 testing foundation

### Advanced Development

- [ ] JDBC connection management
- [ ] Customer JDBC repository
- [ ] Account JDBC repository
- [ ] Transaction JDBC repository
- [ ] Database transaction/rollback support
- [ ] Persistent audit logs
- [ ] Advanced fraud rules
- [ ] Concurrent transaction queue
- [ ] Retry and failure handling
- [ ] Factory implementation
- [ ] Strategy implementation
- [ ] Builder implementation
- [ ] Advanced transaction analytics
- [ ] Integration tests
- [ ] Performance testing
- [ ] Production-style configuration
- [ ] Final architecture documentation

## Why Core Java Instead of Spring Boot?

This project intentionally avoids Spring Boot to understand what frameworks abstract away.

```text
Core Java
   │
   ├── OOP
   ├── Collections
   ├── Generics
   ├── Exceptions
   ├── Concurrency
   ├── JDBC
   ├── SQL
   ├── Design Patterns
   └── Testing
```

## Learning Outcomes

- Designing Java domain models
- Building service-layer business logic
- Writing reusable generic repositories
- Working directly with JDBC
- Managing MySQL persistence
- Handling concurrent operations
- Preventing common concurrency problems
- Designing transaction validation
- Building fraud-detection rules
- Applying design patterns
- Writing JUnit 5 tests
- Processing data with Streams
- Structuring an enterprise-style Java application

## Author

**Saurabh Kushwaha**

Software Developer | Technical Project Coordinator | Java & Backend Development

GitHub: `https://github.com/Github-Saurabh0`

## License

This project is licensed under the **MIT License**.

See the `LICENSE` file for details.

---

> **Built with Core Java to understand the engineering beneath enterprise frameworks.**
