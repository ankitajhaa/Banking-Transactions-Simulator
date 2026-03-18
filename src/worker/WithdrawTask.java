package worker;

import model.Transaction;
import service.TransactionService;

import java.util.concurrent.Callable;

public class WithdrawTask implements Callable<Transaction> {

    private final TransactionService service;
    private final String accountId;
    private final double amount;

    public WithdrawTask(TransactionService service, String accountId, double amount) {
        this.service   = service;
        this.accountId = accountId;
        this.amount    = amount;
    }

    @Override
    public Transaction call() {
        System.out.println(Thread.currentThread().getName()
                + " | Withdrawing ₹" + amount + " from " + accountId);
        return service.withdraw(accountId, amount);
    }
}