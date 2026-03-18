package service;

import model.Account;
import model.Transaction;
import model.Transaction.Status;
import model.Transaction.Type;

import java.util.concurrent.ConcurrentHashMap;

public class TransactionService {

    private final ConcurrentHashMap<String, Account> accounts        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Transaction> transactionLog = new ConcurrentHashMap<>();

    private static final long WAIT_TIMEOUT_MS = 3000;

    public void addAccount(Account account) {
        accounts.put(account.getAccountId(), account);
    }

    public Account getAccount(String id) {
        return accounts.get(id);
    }

    public void logTransaction(Transaction t) {
        transactionLog.put(t.getTransactionId(), t);
        System.out.println(Thread.currentThread().getName() + " → " + t);
    }

    // ── DEPOSIT ──────────────────────────────────────────────
    public Transaction deposit(String accountId, double amount) {
        Transaction t = new Transaction(Type.DEPOSIT, amount);

        if (!validate(amount, accountId, t)) return t;

        Account account = accounts.get(accountId);

        synchronized (account) {
            account.deposit(amount);
            t.setStatus(Status.SUCCESS);
            account.notifyAll(); // ← wake ALL threads waiting for funds on this account
        }

        logTransaction(t);
        return t;
    }

    // ── WITHDRAW ─────────────────────────────────────────────
    public Transaction withdraw(String accountId, double amount) {
        Transaction t = new Transaction(Type.WITHDRAW, amount);

        if (!validate(amount, accountId, t)) return t;

        Account account = accounts.get(accountId);

        synchronized (account) {
            long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;

            // Loop — MUST re-check condition after every wait() call
            // because notifyAll() can wake spuriously or for other reasons
            while (account.getBalance() < amount) {
                long remaining = deadline - System.currentTimeMillis();

                if (remaining <= 0) {
                    // Timeout — give up
                    t.setStatus(Status.TIMEOUT);
                    System.out.println("[TIMEOUT] ₹" + amount
                            + " withdrawal failed | Balance: ₹" + account.getBalance());
                    logTransaction(t);
                    return t;
                }

                try {
                    System.out.println(Thread.currentThread().getName()
                            + " | Waiting for funds... need ₹" + amount
                            + ", have ₹" + account.getBalance()
                            + " (timeout in " + remaining + "ms)");

                    account.wait(remaining); // releases lock, sleeps, re-acquires on wake

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // restore interrupt flag
                    t.setStatus(Status.FAILED);
                    System.out.println("[INTERRUPTED] Withdrawal of ₹" + amount + " interrupted");
                    logTransaction(t);
                    return t;
                }
            }

            // Sufficient funds confirmed inside synchronized block
            account.withdraw(amount);
            t.setStatus(Status.SUCCESS);
        }

        logTransaction(t);
        return t;
    }

    // ── TRANSFER ─────────────────────────────────────────────
    public Transaction transfer(String fromId, String toId, double amount) {
        Transaction t = new Transaction(Type.TRANSFER, amount);

        if (amount <= 0) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] Transfer amount must be > 0");
            logTransaction(t);
            return t;
        }
        if (fromId.equals(toId)) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] Cannot transfer to same account");
            logTransaction(t);
            return t;
        }

        Account from = accounts.get(fromId);
        Account to   = accounts.get(toId);

        if (from == null || to == null) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] One or both accounts not found");
            logTransaction(t);
            return t;
        }

        // Deadlock prevention — consistent lock ordering by accountId
        Account first  = fromId.compareTo(toId) < 0 ? from : to;
        Account second = fromId.compareTo(toId) < 0 ? to   : from;

        synchronized (first) {
            synchronized (second) {
                if (from.getBalance() < amount) {
                    t.setStatus(Status.FAILED);
                    System.out.println("[FAILED] Insufficient funds for transfer | Balance: ₹"
                            + from.getBalance());
                    logTransaction(t);
                    return t;
                }
                from.withdraw(amount);
                to.deposit(amount);
                t.setStatus(Status.SUCCESS);

                // Notify anyone waiting on the destination account
                to.notifyAll();
            }
        }

        logTransaction(t);
        return t;
    }

    // ── SHARED VALIDATION ─────────────────────────────────────
    private boolean validate(double amount, String accountId, Transaction t) {
        if (amount <= 0) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] Amount must be > 0");
            logTransaction(t);
            return false;
        }
        if (!accounts.containsKey(accountId)) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] Account not found: " + accountId);
            logTransaction(t);
            return false;
        }
        return true;
    }

    public ConcurrentHashMap<String, Transaction> getTransactionLog() {
        return transactionLog;
    }
}