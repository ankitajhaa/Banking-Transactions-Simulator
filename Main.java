public class Main {
    public static void main(String[] args) {

        TransactionService service = new TransactionService();

        Account acc1 = new Account("A1", 5000);
        Account acc2 = new Account("A2", 3000);

        service.addAccount(acc1);
        service.addAccount(acc2);

        service.deposit("A1", 1000);     
        service.withdraw("A2", 500);     
        service.transfer("A1", "A2", 2000);

        System.out.println("A1 Balance: " + service.getAccount("A1").getBalance());
        System.out.println("A2 Balance: " + service.getAccount("A2").getBalance());
    }
}