package model;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Account {
    private final String accountId;
    private double balance;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public Account(String accountId, double initialBalance) {
        this.accountId = accountId;
        this.balance = initialBalance;
    }

    public String getAccountId() { return accountId; }

    // Read lock — multiple threads can read simultaneously
    public double getBalance() {
        lock.readLock().lock();
        try {
            return balance;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Write lock — exclusive access for mutations
    public void deposit(double amount) {
        lock.writeLock().lock();
        try {
            balance += amount;
            notifyAll(); // wake threads waiting for funds
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean withdraw(double amount) {
        lock.writeLock().lock();
        try {
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ReentrantReadWriteLock getLock() { return lock; }

    @Override
    public String toString() {
        return "Account[" + accountId + ", balance=" + getBalance() + "]";
    }
}