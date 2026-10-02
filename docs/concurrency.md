# Concurrency Design

The project uses:

- `ReentrantLock` for account-level critical sections.
- `ExecutorService` for asynchronous transaction processing.
- `CopyOnWriteArrayList` for thread-safe audit event storage.
- Consistent account lock ordering for transfers.

Future extensions can add `BlockingQueue`, idempotency keys, transaction workers, and retry handling.
