package service;

import model.Account;
import model.Transaction;
import model.Transaction.Status;
import model.Transaction.Type;

import java.util.concurrent.ConcurrentHashMap;

public class TransactionService {

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Transaction> transactionLog = new ConcurrentHashMap<>();

    private static final long WAIT_TIMEOUT_MS = 3000; // 3 seconds

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

        // Input validation
        if (amount <= 0) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] Deposit amount must be > 0");
            logTransaction(t);
            return t;
        }

        Account account = accounts.get(accountId);
        if (account == null) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] Account not found: " + accountId);
            logTransaction(t);
            return t;
        }

        // Synchronized block for wait/notify (covered fully in Checkpoint 4)
        synchronized (account) {
            account.deposit(amount);
            t.setStatus(Status.SUCCESS);
            account.notifyAll(); // wake any threads waiting for funds
        }

        logTransaction(t);
        return t;
    }

    // ── WITHDRAW ─────────────────────────────────────────────
    public Transaction withdraw(String accountId, double amount) {
        Transaction t = new Transaction(Type.WITHDRAW, amount);

        // Input validation
        if (amount <= 0) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] Withdrawal amount must be > 0");
            logTransaction(t);
            return t;
        }

        Account account = accounts.get(accountId);
        if (account == null) {
            t.setStatus(Status.FAILED);
            System.out.println("[INVALID] Account not found: " + accountId);
            logTransaction(t);
            return t;
        }

        synchronized (account) {
            long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;

            // Wait if insufficient funds (with timeout)
            while (account.getBalance() < amount) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    t.setStatus(Status.TIMEOUT);
                    System.out.println("[TIMEOUT] Insufficient funds for withdrawal of ₹" + amount
                            + " | Balance: ₹" + account.getBalance());
                    logTransaction(t);
                    return t;
                }
                try {
                    account.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    t.setStatus(Status.FAILED);
                    logTransaction(t);
                    return t;
                }
            }

            // Funds available — proceed
            account.withdraw(amount);
            t.setStatus(Status.SUCCESS);
        }

        logTransaction(t);
        return t;
    }

    // ── TRANSFER ─────────────────────────────────────────────
    public Transaction transfer(String fromId, String toId, double amount) {
        Transaction t = new Transaction(Type.TRANSFER, amount);

        // Input validation
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

        // Deadlock prevention — always lock in consistent order by accountId
        Account first  = fromId.compareTo(toId) < 0 ? from : to;
        Account second = fromId.compareTo(toId) < 0 ? to   : from;

        synchronized (first) {
            synchronized (second) {
                if (from.getBalance() < amount) {
                    t.setStatus(Status.FAILED);
                    System.out.println("[FAILED] Insufficient funds for transfer of ₹" + amount
                            + " | Balance: ₹" + from.getBalance());
                    logTransaction(t);
                    return t;
                }

                from.withdraw(amount);
                to.deposit(amount);
                t.setStatus(Status.SUCCESS);
            }
        }

        logTransaction(t);
        return t;
    }

    public ConcurrentHashMap<String, Transaction> getTransactionLog() {
        return transactionLog;
    }
}