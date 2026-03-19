package model;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Account {
    private final String accountId;
    private double balance;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock  readLock  = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    public Account(String accountId, double initialBalance) {
        this.accountId = accountId;
        this.balance = initialBalance;
    }

    public String getAccountId() { return accountId; }

    public double getBalance() {
        readLock.lock();
        try {
            return balance;
        } finally {
            readLock.unlock();
        }
    }

    public void deposit(double amount) {
        writeLock.lock();
        try {
            balance += amount;
            notifyAll(); 
        } finally {
            writeLock.unlock();
        }
    }

    public boolean withdraw(double amount) {
        writeLock.lock();
        try {
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public String toString() {
        return "Account[" + accountId + ", ₹" + getBalance() + "]";
    }
}