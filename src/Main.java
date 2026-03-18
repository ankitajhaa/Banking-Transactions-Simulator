import fraud.FraudDetector;
import model.Account;
import model.Transaction;
import service.TransactionService;
import worker.DepositTask;
import worker.WithdrawTask;
import worker.TransferTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException {

        TransactionService service = new TransactionService();
        Account acc1 = new Account("ACC001", 5000);
        Account acc2 = new Account("ACC002", 3000);
        service.addAccount(acc1);
        service.addAccount(acc2);

        Thread fraudThread = new Thread(new FraudDetector());
        fraudThread.setDaemon(true);
        fraudThread.setName("FraudDetector");
        fraudThread.start();

        ExecutorService executor = Executors.newFixedThreadPool(5);

        System.out.println("═══ Submitting concurrent transactions ═══\n");

        Future<Transaction> deposit1  = executor.submit(new DepositTask(service,  "ACC001", 5000));
        Future<Transaction> withdraw1 = executor.submit(new WithdrawTask(service, "ACC001", 2000));
        Future<Transaction> withdraw2 = executor.submit(new WithdrawTask(service, "ACC001", 7000)); // will timeout
        Future<Transaction> transfer1 = executor.submit(new TransferTask(service, "ACC001", "ACC002", 3000));

        executor.submit((Runnable) () -> {
            System.out.println(Thread.currentThread().getName()
                    + " | [Runnable] Balance check ACC001: ₹" + acc1.getBalance());
        });
        executor.submit((Runnable) () -> {
            System.out.println(Thread.currentThread().getName()
                    + " | [Runnable] Balance check ACC002: ₹" + acc2.getBalance());
        });

        System.out.println("\n═══ Stress test: 10 concurrent tasks ═══\n");
        List<Future<Transaction>> stressFutures = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            stressFutures.add(executor.submit(new DepositTask(service,  "ACC001", 500)));
            stressFutures.add(executor.submit(new WithdrawTask(service, "ACC001", 300)));
        }

        System.out.println("\n═══ Results ═══\n");
        System.out.println(deposit1.get(5,  TimeUnit.SECONDS));
        System.out.println(withdraw1.get(5, TimeUnit.SECONDS));
        System.out.println(withdraw2.get(5, TimeUnit.SECONDS));
        System.out.println(transfer1.get(5, TimeUnit.SECONDS));

        System.out.println("\n── Stress test results ──");
        for (Future<Transaction> f : stressFutures) {
            System.out.println(f.get(5, TimeUnit.SECONDS));
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("\n" + (finished ? "✓" : "✗") + " Executor shut down cleanly");

        System.out.println("\n═══ Final Balances ═══");
        System.out.println("ACC001: ₹" + acc1.getBalance());
        System.out.println("ACC002: ₹" + acc2.getBalance());
    }
}