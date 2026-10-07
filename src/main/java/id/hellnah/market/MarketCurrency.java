package id.hellnah.market;
import org.bukkit.OfflinePlayer;
/** Register an implementation using Bukkit ServicesManager before enabling a currency of type SERVICE. */
public interface MarketCurrency {
    String id();
    /** false MUST mean no money changed. Exceptions are treated as ambiguous and quarantined. */
    boolean withdraw(OfflinePlayer player,long minorUnits);
    boolean deposit(OfflinePlayer player,long minorUnits);
}
