class TransactionService {

    public void deposit(Account account, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Account account = accounts.get(accountId);
        if (account == null) {
            throw new RuntimeException("Account not found");
        }

        account.setBalance(account.getBalance() + amount);

        System.out.println("Deposited " + amount + " to " + accountId);
    }

    public void withdraw(Account account, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Account account = accounts.get(accountId);
        if (account == null) {
            throw new RuntimeException("Account not found");
        }

        if (account.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        account.setBalance(account.getBalance() - amount);

        System.out.println("Withdrew " + amount + " from " + accountId);
    }

    public void transfer(Account from, Account to, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Account from = accounts.get(fromId);
        Account to = accounts.get(toId);

        if (from == null || to == null) {
            throw new RuntimeException("Invalid account");
        }

        if (from.getBalance() < amount) {
            throw new RuntimeException("Insufficient balance");
        }

        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);

        System.out.println("Transferred " + amount + " from " + fromId + " to " + toId);
    }

    Account acc = InMemoryBankRepository.accounts.get("A1");
}