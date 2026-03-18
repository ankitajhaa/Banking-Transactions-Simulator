package fraud;

import model.Transaction;

public class FraudDetector implements Runnable {
    private static final double LARGE_TXN_THRESHOLD = 10000.0;

    @Override
    public void run() {
        System.out.println("[FraudDetector] Monitoring started (daemon)...");
        while (true) {
            // Logic covered in Checkpoint 5
            try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
        }
    }

    public void inspect(Transaction t) {
        if (t.getAmount() >= LARGE_TXN_THRESHOLD) {
            System.out.println("[FRAUD ALERT] Suspicious transaction: " + t);
        }
    }
}