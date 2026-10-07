package id.hellnah.market;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.lang.reflect.*;
import java.util.*;

final class Currencies {
    record Currency(String id,String label,int decimals,MarketCurrency backend) {
        long parse(String input){return new java.math.BigDecimal(input).movePointRight(decimals).longValueExact();}
        String format(long n){return java.math.BigDecimal.valueOf(n,decimals).toPlainString()+" "+label;}
    }
    static Map<String,Currency> load(ConfigurationSection root)throws Exception {
        Map<String,Currency> out=new LinkedHashMap<>();if(root==null)throw new IllegalArgumentException("currencies missing");
        for(String id:root.getKeys(false)){
            if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Currency ID must be 1..64 letters/digits/underscores/hyphens");
            var c=root.getConfigurationSection(id);if(c==null||!c.getBoolean("enabled",true))continue;
            int decimals=c.getInt("decimals",2);if(decimals<0||decimals>4)throw new IllegalArgumentException("decimals 0..4");
            String type=c.getString("type","VAULT").toUpperCase(Locale.ROOT);MarketCurrency backend;
            if(type.equals("VAULT")){
                Class<?> cls=Class.forName("net.milkbowl.vault.economy.Economy");
                @SuppressWarnings({"rawtypes","unchecked"}) RegisteredServiceProvider<?> rsp=Bukkit.getServicesManager().getRegistration((Class)cls);
                if(rsp==null)throw new IllegalStateException("Vault economy provider missing for "+id);
                Object provider=rsp.getProvider();double scale=Math.pow(10,decimals);
                backend=new MarketCurrency(){public String id(){return id;}private boolean run(String method,OfflinePlayer p,long amount){try{Object r=cls.getMethod(method,OfflinePlayer.class,double.class).invoke(provider,p,amount/scale);return (boolean)r.getClass().getMethod("transactionSuccess").invoke(r);}catch(Exception e){throw new IllegalStateException("Vault ambiguous result",e);}}public boolean withdraw(OfflinePlayer p,long a){return run("withdrawPlayer",p,a);}public boolean deposit(OfflinePlayer p,long a){return run("depositPlayer",p,a);}};
            }else if(type.equals("PLAYERPOINTS")){
                if(decimals!=0)throw new IllegalArgumentException("PlayerPoints decimals must be 0");
                Object plugin=Bukkit.getPluginManager().getPlugin("PlayerPoints");if(plugin==null)throw new IllegalStateException("PlayerPoints missing");Object api=plugin.getClass().getMethod("getAPI").invoke(plugin);
                backend=new MarketCurrency(){public String id(){return id;}private boolean run(String m,OfflinePlayer p,long a){if(a>Integer.MAX_VALUE)throw new IllegalArgumentException("PlayerPoints amount exceeds integer");try{return (boolean)api.getClass().getMethod(m,UUID.class,int.class).invoke(api,p.getUniqueId(),(int)a);}catch(Exception e){throw new IllegalStateException("PlayerPoints ambiguous result",e);}}public boolean withdraw(OfflinePlayer p,long a){return run("take",p,a);}public boolean deposit(OfflinePlayer p,long a){return run("give",p,a);}};
            }else if(type.equals("SERVICE")){
                backend=new MarketCurrency(){public String id(){return id;}private MarketCurrency delegate(){return Bukkit.getServicesManager().getRegistrations(MarketCurrency.class).stream().map(RegisteredServiceProvider::getProvider).filter(x->x!=this&&x.id().equals(id)).findFirst().orElse(null);}public boolean withdraw(OfflinePlayer p,long a){MarketCurrency service=delegate();return service!=null&&service.withdraw(p,a);}public boolean deposit(OfflinePlayer p,long a){MarketCurrency service=delegate();return service!=null&&service.deposit(p,a);}};
            }else throw new IllegalArgumentException("Unknown currency type: "+type);
            out.put(id,new Currency(id,c.getString("label",id),decimals,backend));
        }if(out.size()>20)throw new IllegalArgumentException("Maximum 20 currencies");if(out.isEmpty())throw new IllegalArgumentException("No currency enabled");return out;
    }
}
