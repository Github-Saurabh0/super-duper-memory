# Architecture

The application follows a layered Core Java design:

```text
Main
  |
BankingService
  |
Domain Models ---- Transaction
  |
Repository abstraction
  |
JDBC implementation (planned extension)
  |
MySQL
```

Concurrency-sensitive account operations use `ReentrantLock`. Transfers acquire account locks in account-number order to reduce deadlock risk.
