package model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Transaction {
    public enum Type { DEPOSIT, WITHDRAW, TRANSFER }
    public enum Status { PENDING, SUCCESS, FAILED, TIMEOUT }

    private final String transactionId;
    private final Type type;
    private final double amount;
    private final LocalDateTime timestamp;
    private Status status;

    public Transaction(Type type, double amount) {
        this.transactionId = UUID.randomUUID().toString();
        this.type = type;
        this.amount = amount;
        this.timestamp = LocalDateTime.now();
        this.status = Status.PENDING;
    }

    public String getTransactionId() { return transactionId; }
    public Type getType()            { return type; }
    public double getAmount()        { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Status getStatus()        { return status; }

    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        return "[" + type + " | ₹" + amount + " | " + status + " | " + timestamp + "]";
    }
}