package test;

import model.Account;
import model.Transaction;
import service.TransactionService;
import worker.DepositTask;
import worker.WithdrawTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTest {

    private static final int THREAD_POOL_SIZE = 10;

    public static void main(String[] args) throws InterruptedException {

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║      BANKING LOAD TEST SUITE         ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        runReadHeavyTest();
        Thread.sleep(500); // brief pause between tests

        runWriteHeavyTest();
        Thread.sleep(500);

        runMixedLoadTest();
    }

    // ── TEST 1: Read-Heavy (1000 reads, 100 writes) ──────────
    private static void runReadHeavyTest() throws InterruptedException {
        System.out.println("═══ Test 1: Read-Heavy Load ═══");
        System.out.println("→ 1000 balance checks, 100 write operations\n");

        TransactionService service = new TransactionService();
        Account acc = new Account("ACC001", 100000);
        service.addAccount(acc);
        service.setSilent(true);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        AtomicInteger successfulReads  = new AtomicInteger(0);
        AtomicInteger successfulWrites = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        List<Future<?>> futures = new ArrayList<>();

        // 1000 read tasks
        for (int i = 0; i < 1000; i++) {
            futures.add(executor.submit((Runnable) () -> {
                double balance = acc.getBalance(); // readLock
                if (balance >= 0) successfulReads.incrementAndGet();
            }));
        }

        // 100 write tasks (mix of deposit and withdraw)
        for (int i = 0; i < 100; i++) {
            if (i % 2 == 0) {
                futures.add(executor.submit(new DepositTask(service, "ACC001", 100)));
            } else {
                futures.add(executor.submit(new WithdrawTask(service, "ACC001", 50)));
            }
        }

        // Wait for all
        for (Future<?> f : futures) {
            try { f.get(10, TimeUnit.SECONDS); }
            catch (ExecutionException | TimeoutException e) {
                successfulWrites.decrementAndGet();
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Count write results
        successfulWrites.set(100); // all submitted writes

        printMetrics("Read-Heavy", duration, 1000, 100,
                successfulReads.get(), acc.getBalance(), 100000);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ── TEST 2: Write-Heavy (1000 deposits/withdrawals) ──────
    private static void runWriteHeavyTest() throws InterruptedException {
        System.out.println("\n═══ Test 2: Write-Heavy Load ═══");
        System.out.println("→ 1000 deposits and withdrawals\n");

        TransactionService service = new TransactionService();
        Account acc = new Account("ACC001", 500000); // high balance — avoid timeouts
        service.addAccount(acc);
        service.setSilent(true);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        List<Future<Transaction>> futures = new ArrayList<>();

        // 500 deposits + 500 withdrawals
        for (int i = 0; i < 500; i++) {
            futures.add(executor.submit(new DepositTask(service,  "ACC001", 200)));
            futures.add(executor.submit(new WithdrawTask(service, "ACC001", 100)));
        }

        for (Future<Transaction> f : futures) {
            try {
                Transaction t = f.get(10, TimeUnit.SECONDS);
                if (t.getStatus() == Transaction.Status.SUCCESS) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (ExecutionException | TimeoutException e) {
                failCount.incrementAndGet();
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        printMetrics("Write-Heavy", duration, 0, 1000,
                successCount.get(), acc.getBalance(), 500000);

        System.out.println("  Successful: " + successCount.get()
                + " | Failed/Timeout: " + failCount.get());

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ── TEST 3: Mixed Load (70% reads, 30% writes) ───────────
    private static void runMixedLoadTest() throws InterruptedException {
        System.out.println("\n═══ Test 3: Mixed Load (70% reads / 30% writes) ═══");
        System.out.println("→ 700 balance checks, 300 write operations\n");

        TransactionService service = new TransactionService();
        Account acc = new Account("ACC001", 200000);
        service.addAccount(acc);
        service.setSilent(true);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        AtomicInteger reads    = new AtomicInteger(0);
        AtomicInteger writes   = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        List<Future<?>> futures = new ArrayList<>();

        // 700 reads
        for (int i = 0; i < 700; i++) {
            futures.add(executor.submit((Runnable) () -> {
                acc.getBalance();
                reads.incrementAndGet();
            }));
        }

        // 300 writes (150 deposits, 150 withdrawals)
        for (int i = 0; i < 300; i++) {
            if (i % 2 == 0) {
                futures.add(executor.submit(new DepositTask(service, "ACC001", 500)));
            } else {
                futures.add(executor.submit(new WithdrawTask(service, "ACC001", 300)));
            }
        }

        for (Future<?> f : futures) {
            try {
                Object result = f.get(10, TimeUnit.SECONDS);
                if (result instanceof Transaction t) {
                    if (t.getStatus() == Transaction.Status.SUCCESS) writes.incrementAndGet();
                    else failures.incrementAndGet();
                }
            } catch (ExecutionException | TimeoutException e) {
                failures.incrementAndGet();
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        printMetrics("Mixed Load", duration, reads.get(), writes.get() + failures.get(),
                reads.get(), acc.getBalance(), 200000);

        System.out.println("  Write success: " + writes.get()
                + " | Write failures: " + failures.get());

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ── Metrics printer ───────────────────────────────────────
    private static void printMetrics(String testName, long durationMs,
                                     int reads, int writes,
                                     int successfulReads, double finalBalance,
                                     double initialBalance) {

        int totalOps   = reads + writes;
        double throughput = totalOps / (durationMs / 1000.0);

        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ " + testName + " Results");
        System.out.println("├─────────────────────────────────────┤");
        System.out.println("│ Total operations : " + totalOps);
        System.out.println("│ Read operations  : " + reads);
        System.out.println("│ Write operations : " + writes);
        System.out.println("│ Successful reads : " + successfulReads);
        System.out.println("│ Execution time   : " + durationMs + " ms");
        System.out.printf( "│ Throughput       : %.0f ops/sec%n", throughput);
        System.out.println("│ Initial balance  : ₹" + initialBalance);
        System.out.println("│ Final balance    : ₹" + finalBalance);
        System.out.println("│ Balance integrity: "
                + (finalBalance >= 0 ? "✓ No overdraft" : "✗ OVERDRAFT DETECTED"));
        System.out.println("└─────────────────────────────────────┘");
    }
}