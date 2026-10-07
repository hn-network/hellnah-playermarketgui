package id.hellnah.market;

import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Single-server transactional escrow. All callers run on the Paper main thread. */
public final class Store implements AutoCloseable {
    private final Connection db;
    private final String prefix;
    private final String lockName;
    public record Listing(long id,String seller,String name,byte[] item,String material,String label,long price,String currency,long created,long expires,String state,String holder,long net,String search) {}
    public record Payout(long id,String owner,String currency,long amount,String state) {}
    public Store(DatabaseSettings settings) throws Exception {
        prefix = settings.prefix();
        lockName = "hnpm:" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((settings.database() + ":" + prefix).getBytes(StandardCharsets.UTF_8))).substring(0, 48);
        Driver driver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
        db = driver.connect(settings.url(), settings.properties());
        if (db == null) throw new SQLException("MySQL driver rejected URL");
        try {
            db.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try (var stmt = prepare("SELECT GET_LOCK(?, 0)")) {
                stmt.setString(1, lockName);
                try (var result = stmt.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1)
                        throw new SQLException("Market database/table-prefix already used by another server, or advisory lock unavailable");
                }
            }
            String engine = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";
            sql("CREATE TABLE IF NOT EXISTS listings (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, seller VARCHAR(64) NOT NULL, name VARCHAR(16) NOT NULL, item MEDIUMBLOB NOT NULL, material VARCHAR(128) NOT NULL, label MEDIUMTEXT NOT NULL, search MEDIUMTEXT NOT NULL, price BIGINT NOT NULL CHECK(price>0), currency VARCHAR(64) NOT NULL, created BIGINT NOT NULL, expires BIGINT NOT NULL, state VARCHAR(24) NOT NULL, holder VARCHAR(64), net BIGINT NOT NULL DEFAULT 0, INDEX listing_state(state,seller,holder))" + engine);
            sql("CREATE TABLE IF NOT EXISTS currency_defs (id VARCHAR(64) PRIMARY KEY, decimals INT NOT NULL, type VARCHAR(24) NOT NULL)" + engine);
            sql("CREATE TABLE IF NOT EXISTS balances (owner VARCHAR(64) NOT NULL, currency VARCHAR(64) NOT NULL, amount BIGINT NOT NULL CHECK(amount>=0), PRIMARY KEY(owner,currency))" + engine);
            sql("CREATE TABLE IF NOT EXISTS payouts (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, owner VARCHAR(64) NOT NULL, currency VARCHAR(64) NOT NULL, amount BIGINT NOT NULL, state VARCHAR(24) NOT NULL, INDEX payout_state(state))" + engine);
            sql("CREATE TABLE IF NOT EXISTS audit (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, time BIGINT NOT NULL, actor VARCHAR(64) NOT NULL, action VARCHAR(64) NOT NULL, ref VARCHAR(128) NOT NULL)" + engine);
            // Existing tables must remain transactional, too; do not accept manually altered MyISAM tables.
            try (var stmt = prepare("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
                for (String table : List.of("listings", "currency_defs", "balances", "payouts", "audit")) {
                    stmt.setString(1, settings.database()); stmt.setString(2, prefix + table);
                    try (var result = stmt.executeQuery()) {
                        if (!result.next() || !"InnoDB".equalsIgnoreCase(result.getString(1)))
                            throw new SQLException("Market tables must use InnoDB");
                    }
                }
            }
        } catch (Exception error) {
            try { db.close(); } catch (SQLException closeError) { error.addSuppressed(closeError); }
            throw error;
        }
    }
    // Only known table identifiers are interpolated, after strict prefix validation.
    private String query(String sql) {
        return sql.replaceAll("\\b(listings|currency_defs|balances|payouts|audit)\\b", "`" + prefix + "$1`");
    }
    private PreparedStatement prepare(String sql) throws SQLException { return db.prepareStatement(query(sql)); }
    private PreparedStatement prepare(String sql, int generatedKeys) throws SQLException { return db.prepareStatement(query(sql), generatedKeys); }
    private void sql(String q) throws SQLException {try(var s=db.createStatement()){s.execute(query(q));}}
    private int update(String q,Object...args) throws SQLException {try(var s=prepare(q)){bind(s,args);return s.executeUpdate();}}
    private static void bind(PreparedStatement s,Object...args)throws SQLException{for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);}
    private <T> T tx(Work<T> work) throws SQLException {
        db.setAutoCommit(false);
        try {
            T result = work.run(); db.commit(); return result;
        } catch (SQLException | RuntimeException error) {
            try { db.rollback(); } catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
            throw error;
        } finally { db.setAutoCommit(true); }
    }
    private interface Work<T>{T run()throws SQLException;}
    public void audit(String actor,String action,String ref)throws SQLException{update("INSERT INTO audit(time,actor,action,ref) VALUES(?,?,?,?)",System.currentTimeMillis(),actor,action,ref);}
    public long prepareListing(String owner,String name,byte[] item,String material,String label,String search,long price,String currency,long expires,int limit)throws SQLException{
        return tx(()->{if(count(owner)>=limit)throw new SQLException("Kapasitas market penuh");try(var s=prepare("INSERT INTO listings(seller,name,item,material,label,search,price,currency,created,expires,state,holder) VALUES(?,?,?,?,?,?,?,?,?,?,'LIST_PREP',?)",Statement.RETURN_GENERATED_KEYS)){bind(s,owner,name,item,material,label,search,price,currency,System.currentTimeMillis(),expires,owner);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}});
    }
    public int count(String owner)throws SQLException{try(var s=prepare("SELECT COUNT(*) FROM listings WHERE seller=? AND state IN ('ACTIVE','LIST_PREP','BUY_PREP')")){s.setString(1,owner);try(var r=s.executeQuery()){r.next();return r.getInt(1);}}}
    public boolean transition(long id,String from,String to,String holder)throws SQLException{return update("UPDATE listings SET state=?,holder=? WHERE id=? AND state=?",to,holder,id,from)==1;}
    public List<Listing> all()throws SQLException{List<Listing> out=new ArrayList<>();try(var s=db.createStatement();var r=s.executeQuery(query("SELECT id,seller,name,NULL AS item,material,label,search,price,currency,created,expires,state,holder,net FROM listings WHERE state<>'DONE' ORDER BY id DESC"))){while(r.next())out.add(row(r));}return out;}
    public Map<Long, byte[]> itemPayloads(Collection<Long> ids) throws SQLException {
        if (ids.isEmpty()) return Map.of();
        if (ids.size() > 45) throw new IllegalArgumentException("At most 45 payloads per GUI page");
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, byte[]> items = new HashMap<>();
        try (var stmt = prepare("SELECT id,item FROM listings WHERE id IN (" + placeholders + ")")) {
            int index = 1; for (Long id : ids) stmt.setLong(index++, id);
            try (var result = stmt.executeQuery()) {
                while (result.next()) items.put(result.getLong(1), result.getBytes(2));
            }
        }
        return items;
    }
    public Listing get(long id)throws SQLException{try(var s=prepare("SELECT * FROM listings WHERE id=?")){s.setLong(1,id);try(var r=s.executeQuery()){return r.next()?row(r):null;}}}
    private Listing row(ResultSet r)throws SQLException{return new Listing(r.getLong("id"),r.getString("seller"),r.getString("name"),r.getBytes("item"),r.getString("material"),r.getString("label"),r.getLong("price"),r.getString("currency"),r.getLong("created"),r.getLong("expires"),r.getString("state"),r.getString("holder"),r.getLong("net"),r.getString("search"));}
    public void expire(long now)throws SQLException{update("UPDATE listings SET state='CLAIMABLE',holder=seller WHERE state='ACTIVE' AND expires<=?",now);}
    public boolean reserve(long id,String buyer,long now,long net)throws SQLException{return update("UPDATE listings SET state='BUY_PREP',holder=?,net=? WHERE id=? AND state='ACTIVE' AND seller<>? AND expires>?",buyer,net,id,buyer,now)==1;}
    private void credit(String owner,String currency,long amount)throws SQLException{long current=balances(owner).getOrDefault(currency,0L);if(amount<0||amount>1000000000000000L-current)throw new SQLException("Market balance limit reached; transaction quarantined");update("INSERT INTO balances(owner,currency,amount) VALUES(?,?,?) ON DUPLICATE KEY UPDATE amount=amount+?",owner,currency,amount,amount);}
    public void completePurchase(long id)throws SQLException{tx(()->{Listing l=get(id);if(l==null||!l.state.equals("BUY_PREP"))throw new SQLException("Listing tidak tersedia");long net=l.net;if(net<0||net>l.price)throw new SQLException("Invalid net");credit(l.seller,l.currency,net);transition(id,"BUY_PREP","CLAIMABLE",l.holder);audit(l.holder,"BUY",Long.toString(id));return null;});}
    public boolean cancel(long id,String owner)throws SQLException{return update("UPDATE listings SET state='CLAIMABLE',holder=seller WHERE id=? AND seller=? AND state='ACTIVE'",id,owner)==1;}
    public Map<String,Long> balances(String owner)throws SQLException{Map<String,Long> out=new TreeMap<>();try(var s=prepare("SELECT currency,amount FROM balances WHERE owner=? AND amount>0")){s.setString(1,owner);try(var r=s.executeQuery()){while(r.next())out.put(r.getString(1),r.getLong(2));}}return out;}
    public long preparePayout(String owner,String currency)throws SQLException{return tx(()->{long amount=balances(owner).getOrDefault(currency,0L);if(amount<=0)throw new SQLException("Saldo kosong");update("UPDATE balances SET amount=0 WHERE owner=? AND currency=?",owner,currency);try(var s=prepare("INSERT INTO payouts(owner,currency,amount,state) VALUES(?,?,?,'PAY_PREP')",Statement.RETURN_GENERATED_KEYS)){bind(s,owner,currency,amount);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();return r.getLong(1);}}});}
    public Payout payout(long id)throws SQLException{try(var s=prepare("SELECT * FROM payouts WHERE id=?")){s.setLong(1,id);try(var r=s.executeQuery()){return r.next()?new Payout(id,r.getString("owner"),r.getString("currency"),r.getLong("amount"),r.getString("state")):null;}}}
    public void finishPayout(long id,boolean success)throws SQLException{tx(()->{Payout p=payout(id);if(p==null||!p.state.equals("PAY_PREP"))throw new SQLException("Payout bukan pending");if(!success)credit(p.owner,p.currency,p.amount);update("UPDATE payouts SET state=? WHERE id=? AND state='PAY_PREP'",success?"DONE":"FAILED",id);return null;});}
    public List<Payout> pendingPayouts()throws SQLException{List<Payout> out=new ArrayList<>();try(var s=db.createStatement();var r=s.executeQuery(query("SELECT * FROM payouts WHERE state='PAY_PREP'"))){while(r.next())out.add(new Payout(r.getLong("id"),r.getString("owner"),r.getString("currency"),r.getLong("amount"),r.getString("state")));}return out;}

    public void currencyDefinition(String id,int decimals,String type)throws SQLException {
        try(var s=prepare("SELECT decimals,type FROM currency_defs WHERE id=?")){s.setString(1,id);try(var r=s.executeQuery()){if(r.next()){if(r.getInt(1)!=decimals||!r.getString(2).equals(type))throw new IllegalArgumentException("Currency "+id+" already stored with different decimals/type");return;}}}
        update("INSERT INTO currency_defs(id,decimals,type) VALUES(?,?,?)",id,decimals,type);
    }
    public void requireCurrencies(Set<String> ids)throws SQLException {
        try(var s=db.createStatement();var r=s.executeQuery(query("SELECT currency FROM listings WHERE state<>'DONE' UNION SELECT currency FROM balances WHERE amount>0 UNION SELECT currency FROM payouts WHERE state='PAY_PREP'"))){
            while(r.next())if(!ids.contains(r.getString(1)))throw new IllegalArgumentException("Currency still in escrow: "+r.getString(1));
        }
    }
    @Override public void close() throws SQLException {
        // Closing the dedicated connection releases the advisory lock as well.
        db.close();
    }
}
