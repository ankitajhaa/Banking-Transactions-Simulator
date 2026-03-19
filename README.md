# Banking Transaction Simulator

A multi-threaded banking system simulator built in Java that demonstrates
safe and efficient concurrent transaction handling.

## Problem Statement

Banks handle thousands of transactions simultaneously. Without proper
concurrency control, this leads to race conditions, deadlocks, incorrect
balances, and inconsistent system states. This simulator tackles all of
these problems head-on.

---

## Architecture
```
Client Threads (Deposit / Withdraw / Transfer)
        ↓
Worker Layer (Callable Tasks via ExecutorService)
        ↓
Service Layer (TransactionService — business logic)
        ↓
Account Layer (Shared Resource — ReentrantReadWriteLock)
        ↓
Fraud Detection (Daemon Thread — background monitoring)
```

## Project Structure
```
banking-trans/
├── .vscode/
│   └── settings.json
└── src/
    ├── model/
    │   ├── Account.java          
    │   └── Transaction.java      
    ├── service/
    │   └── TransactionService.java
    ├── fraud/
    │   └── FraudDetector.java    
    ├── worker/
    │   ├── DepositTask.java      
    │   ├── WithdrawTask.java     
    │   └── TransferTask.java     
    ├── test/
    │   └── LoadTest.java         
    └── Main.java                 
```

---

## Features & Design Decisions

### 1. Concurrent Transactions
- `ExecutorService` with a fixed thread pool of 5 — no manual `new Thread()`
- All tasks implement `Callable<Transaction>` and return results via `Future`
- Both `Runnable` (fire-and-forget balance checks) and `Callable` (transactional
  tasks with results) are demonstrated

### 2. Thread Safety — ReentrantReadWriteLock
- Every `Account` holds its own `ReentrantReadWriteLock`
- `readLock` → `getBalance()` — multiple threads read simultaneously
- `writeLock` → `deposit()` / `withdraw()` — exclusive access for mutations
- Why not a plain lock? A plain `synchronized` would queue ALL threads even
  for reads that don't conflict. `ReadWriteLock` allows concurrent reads,
  which is critical for read-heavy banking systems.

### 3. Read-Heavy Optimization
Proven by load testing:
- Write-Heavy workload:  ~52,000 ops/sec
- Mixed 70/30 workload: ~142,000 ops/sec (2.7x faster)

The speedup comes entirely from concurrent reads via `readLock`.

### 4. Inter-thread Communication — wait/notify
- Withdrawal checks balance inside `synchronized(account)` block
- If insufficient funds → `account.wait(timeout)` releases lock and sleeps
- Deposit calls `account.notifyAll()` to wake all waiting threads
- Waiting thread uses a `while` loop (not `if`) to re-check condition
  after every wakeup — handles spurious wakeups correctly
- Configurable timeout (default 3 seconds) — returns `TIMEOUT` status
  instead of blocking indefinitely

### 5. Fraud Detection — Daemon Thread
- Runs as a daemon thread — automatically stops when JVM exits
- Scans transaction log every 500ms
- Two detection rules:
  - **Rule 1**: Flags any single transaction ≥ ₹10,000
  - **Rule 2**: Flags 3+ withdrawals within a 5-second sliding window
- Uses `ConcurrentLinkedQueue` for thread-safe timestamp tracking

### 6. Async Transaction Results — Future & Callable
- All worker tasks return `Future<Transaction>`
- Results retrieved with `future.get(5, TimeUnit.SECONDS)`
- Full exception handling for all 4 Future outcomes:
  - `TimeoutException` → task took too long → cancel it
  - `CancellationException` → task was cancelled mid-flight
  - `ExecutionException` → task threw internally
  - `InterruptedException` → waiting thread was interrupted
- Batch execution via `invokeAll()` for stress testing

### 7. Deadlock Prevention — Ordered Locking
Transfer between two accounts is the classic deadlock scenario:
```
Without fix:
  Thread A: locks ACC001, waits for ACC002
  Thread B: locks ACC002, waits for ACC001  ← circular wait = deadlock

Our fix: always acquire locks in alphabetical order by accountId
  Thread A: locks ACC001 → locks ACC002
  Thread B: locks ACC001 → locks ACC002  ← same order, no circular wait
```
Proven by submitting opposite-direction transfers simultaneously —
both succeed without deadlock.

### 8. Graceful Shutdown
```java
executor.shutdown();                          // stop accepting new tasks
executor.awaitTermination(10, TimeUnit.SECONDS); // wait for running tasks
// fallback:
executor.shutdownNow();                       // force stop if timeout exceeded
```
Ensures no partial updates or orphaned threads on exit.

---

## Concurrency Strategy Summary

| Problem              | Solution                          |
|----------------------|-----------------------------------|
| Race conditions      | ReentrantReadWriteLock            |
| Read-heavy system    | ReadLock allows concurrent reads  |
| Insufficient funds   | wait(timeout) / notifyAll()       |
| Async execution      | ExecutorService + Future          |
| Deadlock             | Ordered locking by accountId      |
| Background monitoring| Daemon thread                     |
| Partial shutdowns    | shutdown() + awaitTermination()   |

---

## Sample Transactions (Main.java)

| Transaction | Amount | Result |
|---|---|---|
| Deposit → ACC001 | ₹5,000 | SUCCESS |
| Withdraw ← ACC001 | ₹2,000 | SUCCESS |
| Withdraw ← ACC001 | ₹7,000 | TIMEOUT (insufficient funds) |
| Transfer ACC001 → ACC002 | ₹3,000 | SUCCESS |
| Large withdrawal | ₹15,000 | FRAUD ALERT triggered |
| 5 rapid withdrawals | ₹100 each | FRAUD ALERT triggered |
| Opposite transfers (deadlock test) | ₹500 each | Both SUCCESS, no deadlock |

---

## Load Test Results

Run `test/LoadTest.java` independently to execute all 3 scenarios:

| Scenario | Operations | Time | Throughput | Result |
|---|---|---|---|---|
| Read-Heavy (1000R / 100W) | 1100 | ~31ms | ~35,000/sec | ✓ No overdraft |
| Write-Heavy (1000W) | 1000 | ~19ms | ~52,000/sec | ✓ No overdraft |
| Mixed (700R / 300W) | 1000 | ~7ms | ~142,000/sec | ✓ No overdraft |

**Key finding**: Mixed load is 2.7x faster than write-heavy — direct proof
that `ReadWriteLock` concurrent reads are working as intended.

---

## How to Run

### Prerequisites
- Java 25 (OpenJDK)
- VS Code with Red Hat Java extension

### Setup
```bash
git clone <repo>
cd banking-trans
```

Add `.vscode/settings.json`:
```json
{
  "java.project.sourcePaths": ["src"]
}
```

### Run Main Simulation
Open `src/Main.java` → click **Run** above the `main` method

### Run Load Tests
Open `src/test/LoadTest.java` → click **Run** above the `main` method

---

## Data Model

**Account**
- `accountId` — unique identifier
- `balance` — protected by `ReentrantReadWriteLock`

**Transaction** (immutable log)
- `transactionId` — UUID
- `type` — DEPOSIT / WITHDRAW / TRANSFER
- `amount`
- `timestamp`
- `status` — PENDING / SUCCESS / FAILED / TIMEOUT

**Storage**
- `ConcurrentHashMap<AccountId, Account>` — thread-safe account store
- `ConcurrentHashMap<TransactionId, Transaction>` — append-only audit log

---

## Future Enhancements
- Persistent database layer
- REST API interface
- Distributed locking (for multi-JVM deployment)
- Real-time monitoring dashboard