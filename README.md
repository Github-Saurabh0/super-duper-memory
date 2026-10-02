# Enterprise Banking System — Core Java

An advanced Core Java banking system designed to demonstrate enterprise-style Java fundamentals without Spring Boot.

## Stack
- Java 17+
- Maven
- JDBC
- MySQL
- Java Collections & Generics
- Streams & Lambdas
- Multithreading & Concurrency
- Design Patterns
- JUnit 5

## Modules
- Customer management
- Savings and current accounts
- Deposits and withdrawals
- Fund transfers
- Transaction records
- Fraud detection
- Audit logging
- Concurrent transaction processing
- JDBC persistence
- Reporting

## Run
```bash
mvn test
mvn package
java -cp target/classes com.saurabh.banking.Main
```

Configure MySQL credentials in `database.properties` or environment variables before enabling JDBC persistence.

## Project philosophy
This project intentionally uses Core Java and JDBC rather than Spring Boot so that the code demonstrates Java fundamentals directly.
