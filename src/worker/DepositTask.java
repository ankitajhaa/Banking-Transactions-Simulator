package worker;

import model.Transaction;
import service.TransactionService;

import java.util.concurrent.Callable;

public class DepositTask implements Callable<Transaction> {

    private final TransactionService service;
    private final String accountId;
    private final double amount;

    public DepositTask(TransactionService service, String accountId, double amount) {
        this.service   = service;
        this.accountId = accountId;
        this.amount    = amount;
    }

    @Override
    public Transaction call() {
        System.out.println(Thread.currentThread().getName()
                + " | Depositing ₹" + amount + " → " + accountId);
        return service.deposit(accountId, amount);
    }
}