import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class InMemoryBankRepository {

    public static final Map<String, Account> accounts = new ConcurrentHashMap<>();
    public static final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
}