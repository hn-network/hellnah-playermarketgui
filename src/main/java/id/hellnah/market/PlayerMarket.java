package id.hellnah.market;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.SQLException;
import java.nio.file.*;

public final class PlayerMarket extends org.bukkit.plugin.java.JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    private Store store;
    private DatabaseSettings storageSettings;
    private Map<String,Currencies.Currency> currencies=Map.of();
    private final Map<UUID,Prompt> prompts=new ConcurrentHashMap<>();
    private final Map<UUID,Long> clicks=new HashMap<>();
    private boolean healthy=true;
    private record Prompt(String type,View back,long expires){}
    private record View(String type,String key,int page,int sort,String currency,View parent){}
    private static final class Menu implements InventoryHolder {
        Inventory inv;final Map<Integer,Runnable> actions=new HashMap<>();
        @Override public Inventory getInventory(){return inv;}
    }
    private interface Checked {void run()throws Exception;}
    @Override public void onEnable(){
        saveDefaultConfig();
        try{validate(); currencies=Currencies.load(getConfig().getConfigurationSection("currencies"));storageSettings=databaseSettings();store=new Store(storageSettings);checkCurrencies(currencies);store.expire(System.currentTimeMillis());}
        catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Market tidak dapat diaktifkan. Periksa config/economy/database.",e);Bukkit.getPluginManager().disablePlugin(this);return;}
        getCommand("pmarket").setExecutor(this);getCommand("pmarket").setTabCompleter(this);Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskTimer(this,()->safe(null,()->store.expire(System.currentTimeMillis())),1200,1200);
        try{long pending=store.all().stream().filter(x->x.state().endsWith("PREP")).count()+store.pendingPayouts().size();if(pending>0)getLogger().warning(pending+" transaksi dikarantina. Gunakan /pmarket pending dan baca PANDUAN.md.");}catch(Exception e){fatal(e);}
    }
    @Override public void onDisable(){
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)p.closeInventory();prompts.clear();
        if(store!=null)try{store.close();}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Close database",e);}
    }
    private DatabaseSettings databaseSettings() {
        if (!"MYSQL".equalsIgnoreCase(getConfig().getString("storage.type", "MYSQL")))
            throw new IllegalArgumentException("Only MYSQL storage is supported in this release");
        String password = getConfig().getString("storage.mysql.password", "");
        String passwordEnv = getConfig().getString("storage.mysql.password-env", "").trim();
        if (!passwordEnv.isEmpty()) {
            password = System.getenv(passwordEnv);
            if (password == null || password.isEmpty()) throw new IllegalArgumentException("Database password environment variable missing");
        }
        return new DatabaseSettings(getConfig().getString("storage.mysql.host", "127.0.0.1"),
                getConfig().getInt("storage.mysql.port", 3306), getConfig().getString("storage.mysql.database", "hellnah_market"),
                getConfig().getString("storage.mysql.username", "hellnah_market"), password,
                getConfig().getString("storage.mysql.table-prefix", "hnpm_"),
                getConfig().getString("storage.mysql.ssl-mode", "REQUIRED"),
                getConfig().getBoolean("storage.mysql.allow-public-key-retrieval", false),
                getConfig().getInt("storage.mysql.connect-timeout-ms", 3000),
                getConfig().getInt("storage.mysql.socket-timeout-ms", 3000));
    }
    private void validate(){
        if(getConfig().getInt("security.max-item-bytes",1048576)<1||getConfig().getInt("security.max-item-bytes",1048576)>16777215)throw new IllegalArgumentException("max-item-bytes 1..16777215");
        if(getConfig().getInt("capacity.slots-per-page",45)<1||getConfig().getInt("capacity.slots-per-page",45)>45)throw new IllegalArgumentException("slots-per-page 1..45");
        if(getConfig().getInt("listing.expiry-hours",72)<1||getConfig().getInt("listing.expiry-hours",72)>8760)throw new IllegalArgumentException("expiry-hours 1..8760");
        if(getConfig().getDouble("listing.tax-percent",0)<0||getConfig().getDouble("listing.tax-percent",0)>100)throw new IllegalArgumentException("tax 0..100");
        for(int i=1;i<=10;i++)if(getConfig().getInt("capacity.levels."+i,i)<1||getConfig().getInt("capacity.levels."+i,i)>10)throw new IllegalArgumentException("pages 1..10");
        if(getConfig().getLong("listing.min-price-minor",1)<1||getConfig().getLong("listing.max-price-minor",100000000000L)>1000000000000L||getConfig().getLong("listing.min-price-minor",1)>getConfig().getLong("listing.max-price-minor",100000000000L))throw new IllegalArgumentException("max-price-minor >0");
    }
    private void fatal(Exception e){healthy=false;getLogger().log(java.util.logging.Level.SEVERE,"Market paused after storage failure; restart after fixing disk/database. Pending operations require inspection.",e);}
    private void safe(Player p,Checked action){if(!healthy){if(p!=null)msg(p,"paused",Map.of());return;}try{action.run();}catch(SQLException e){fatal(e);if(p!=null)msg(p,"paused",Map.of());}catch(Exception e){getLogger().log(java.util.logging.Level.WARNING,"Market operation failed",e);if(p!=null)msg(p,"error",Map.of());}}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
    private void msg(Player p,String key,Map<String,String> args){String t=getConfig().getString("messages."+key,key);for(var e:args.entrySet())t=t.replace("{"+e.getKey()+"}",e.getValue());p.sendMessage(color(getConfig().getString("messages.prefix","&6[Market] &r")+t));}
    private int capacity(Player p){int pages=getConfig().getInt("capacity.default-pages",1);for(int i=1;i<=10;i++)if(p.hasPermission("hellnah.pmarket.pages."+i))pages=Math.max(pages,getConfig().getInt("capacity.levels."+i,i));return Math.max(1,Math.min(10,pages))*getConfig().getInt("capacity.slots-per-page",45);}
    private boolean survival(Player p){if(getConfig().getBoolean("security.block-creative",true)&&(p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR)){msg(p,"creative-blocked",Map.of());return false;}return true;}
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){
        if(args.length>0&&args[0].equalsIgnoreCase("reload")){
            if(!s.hasPermission("hellnah.pmarket.admin")){s.sendMessage("Tidak ada permission.");return true;}
            var old=getConfig();try{reloadConfig();validate();if(!databaseSettings().equals(storageSettings))throw new IllegalArgumentException("Perubahan storage membutuhkan restart server; tidak diterapkan lewat reload");var next=Currencies.load(getConfig().getConfigurationSection("currencies"));
                // Persisted prices are minor units. Prevent accidental reinterpretation of existing escrow.
                for(var e:currencies.entrySet())if(!next.containsKey(e.getKey())||next.get(e.getKey()).decimals()!=e.getValue().decimals())throw new IllegalArgumentException("Cannot remove/change decimals of a currency on reload; restart only after clearing its escrow");
                checkCurrencies(next);currencies=next;for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)p.closeInventory();prompts.clear();s.sendMessage("HellNah PlayerMarket: config berhasil direload.");
            }catch(Exception e){ // restore live config object values on rejected reload
                getConfig().getKeys(false).forEach(k->getConfig().set(k,null));for(String k:old.getKeys(false))getConfig().set(k,old.get(k));s.sendMessage("Reload ditolak: "+e.getMessage());}return true;
        }
        if(args.length>0&&(args[0].equalsIgnoreCase("pending")||args[0].equalsIgnoreCase("resolve"))){admin(s,args);return true;}
        if(!(s instanceof Player p)){s.sendMessage("Gunakan /pmarket reload, pending, atau resolve.");return true;}
        if(!p.hasPermission("hellnah.pmarket.use")){msg(p,"no-permission",Map.of());return true;}
        safe(p,()->{
            store.expire(System.currentTimeMillis());
            if(args.length==0){open(p,new View("HOME","",0,0,"",null));return;}
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "sell"->{if(args.length<2){p.sendMessage(color("&e/pmarket sell <harga total> [mata uang]"));return;}startSale(p,args[1],args.length>2?args[2]:getConfig().getString("default-currency","money"),new View("OWN","",0,0,"",home()));}
                case "claim"->open(p,new View("CLAIMS","",0,0,"",home()));
                case "search"->open(p,new View("SEARCH",String.join(" ",Arrays.copyOfRange(args,1,args.length)),0,0,"",home()));
                case "help"->p.sendMessage(color("&e/pmarket [nama pemain] &7| &e/pmarket sell <harga> [currency] &7| &e/pmarket claim"));
                default->{String name=args[0];var found=store.all().stream().filter(x->x.name().equalsIgnoreCase(name)).findFirst();if(found.isEmpty())msg(p,"shop-missing",Map.of());else open(p,new View("SHOP",found.get().seller(),0,0,"",home()));}
            }
        });return true;
    }
    private View home(){return new View("HOME","",0,0,"",null);}
    private void admin(CommandSender s,String[] args){if(!s.hasPermission("hellnah.pmarket.admin")){s.sendMessage("Tidak ada permission.");return;}try{
        if(args[0].equalsIgnoreCase("pending")){
            s.sendMessage("Pending listings (id/state/seller/holder):");for(var l:store.all())if(l.state().endsWith("PREP"))s.sendMessage(l.id()+" "+l.state()+" "+l.name()+" "+l.holder());for(var q:store.pendingPayouts())s.sendMessage("payout:"+q.id()+" "+q.owner()+" "+q.currency()+" "+q.amount());return;
        }
        if(args.length!=3||(!args[2].equalsIgnoreCase("commit")&&!args[2].equalsIgnoreCase("abort"))){s.sendMessage("/pmarket resolve <listingId|payout:id> <commit|abort>. WAJIB verifikasi item/uang sesuai PANDUAN.");return;}
        boolean commit=args[2].equalsIgnoreCase("commit");String actor=s.getName();
        if(args[1].startsWith("payout:")){long id=Long.parseLong(args[1].substring(7));store.audit(actor,"RESOLVE_INTENT_"+args[2],args[1]);store.finishPayout(id,commit);}
        else{long id=Long.parseLong(args[1]);var l=store.get(id);if(l==null)throw new IllegalArgumentException("ID tidak ditemukan");store.audit(actor,"RESOLVE_INTENT_"+args[2],args[1]);switch(l.state()){
            case "LIST_PREP"->store.transition(id,"LIST_PREP",commit?"ACTIVE":"DONE",commit?null:l.seller());
            case "BUY_PREP"->{if(commit)store.completePurchase(id);else store.transition(id,"BUY_PREP","ACTIVE",null);}
            case "CLAIM_PREP"->store.transition(id,"CLAIM_PREP",commit?"DONE":"CLAIMABLE",l.holder());
            default->throw new IllegalArgumentException("ID bukan transaksi pending");
        }}s.sendMessage("Resolved "+args[1]+" "+args[2]);
    }catch(Exception e){s.sendMessage("Gagal: "+e.getMessage());getLogger().log(java.util.logging.Level.WARNING,"Admin resolution",e);}}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1){List<String> list=new ArrayList<>(List.of("sell","claim","search","help"));if(s.hasPermission("hellnah.pmarket.admin"))list.addAll(List.of("reload","pending","resolve"));return list.stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();}if(args.length==3&&args[0].equalsIgnoreCase("sell"))return new ArrayList<>(currencies.keySet());return List.of();}
    private ItemStack icon(Material mat,String name,String...lore){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.setDisplayName(color(name));m.setLore(Arrays.stream(lore).map(this::color).toList());i.setItemMeta(m);return i;}
    private ItemStack ui(String key,Material mat,String name,String...lore){String path="gui.buttons."+key;Material override=Material.matchMaterial(getConfig().getString(path+".material",mat.name()));if(override==null||override.isAir())override=mat;List<String> lines=getConfig().contains(path+".lore")?getConfig().getStringList(path+".lore"):Arrays.asList(lore);return icon(override,getConfig().getString(path+".name",name),lines.toArray(String[]::new));}
    private Menu menu(String title){Menu m=new Menu();m.inv=Bukkit.createInventory(m,54,color(title));ItemStack glass=icon(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=45;i<54;i++)m.inv.setItem(i,glass);return m;}
    private void button(Menu m,int slot,ItemStack i,Runnable action){m.inv.setItem(slot,i);m.actions.put(slot,action);}
    private String title(String key,String fallback){return getConfig().getString("gui.titles."+key.toLowerCase(Locale.ROOT),fallback);}
    private void open(Player p,View v)throws Exception{
        store.expire(System.currentTimeMillis());Menu m=menu(title(v.type,"HellNah Market"));
        if(v.type.equals("HOME")){
            button(m,11,ui("players",Material.PLAYER_HEAD,"&eMarket Players","&7Shop berdasarkan nama pemain"),()->go(p,new View("PLAYERS","",0,0,"",v)));
            button(m,13,ui("browse",Material.CHEST,"&eBrowse Item","&7Pilih jenis item, lalu pilih penjual"),()->go(p,new View("TYPES","",0,0,"",v)));
            button(m,15,ui("categories",Material.OAK_LEAVES,"&eKategori Item","&7Dekorasi, equipment, dan lainnya"),()->go(p,new View("CATEGORIES","",0,0,"",v)));
            button(m,29,ui("search-item",Material.COMPASS,"&bSearch Item","&7Cari material, nama custom, atau lore"),()->prompt(p,"ITEM",v));
            button(m,31,ui("search-shop",Material.NAME_TAG,"&bSearch Shop","&7Cari nama pemain"),()->prompt(p,"SHOP",v));
            button(m,33,ui("your-market",Material.ENDER_CHEST,"&aYour Market","&7Kapasitas: "+capacity(p)+" listing"),()->go(p,new View("OWN","",0,0,"",v)));
            button(m,40,ui("unclaimed",Material.HOPPER,"&6Unclaimed Items","&7Item dibeli, dibatalkan, atau kedaluwarsa"),()->go(p,new View("CLAIMS","",0,0,"",v)));
        }else if(v.type.equals("CATEGORIES")){
            List<String> cats=new ArrayList<>();var section=getConfig().getConfigurationSection("categories");if(section!=null)cats.addAll(section.getKeys(false));cats.add("Lainnya");for(int i=0;i<cats.size()&&i<45;i++){String cat=cats.get(i);button(m,i,icon(Material.CHEST,"&e"+cat),()->go(p,new View("CATEGORY",cat,0,v.sort,v.currency,v)));}
        }else if(v.type.equals("PLAYERS")||v.type.equals("SHOPSEARCH")){
            Map<String,Store.Listing> sellers=new TreeMap<>();for(var l:store.all())if(l.state().equals("ACTIVE")&&(!v.type.equals("SHOPSEARCH")||l.name().toLowerCase(Locale.ROOT).contains(v.key.toLowerCase(Locale.ROOT))))sellers.put(l.name(),l);
            List<Store.Listing> rows=new ArrayList<>(sellers.values());int start=pageStart(v.page,rows.size());for(int i=start;i<Math.min(start+pageSize(),rows.size());i++){
                var l=rows.get(i);ItemStack head=icon(Material.PLAYER_HEAD,"&e"+l.name(),"&7Klik untuk melihat market");SkullMeta sm=(SkullMeta)head.getItemMeta();sm.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(l.seller())));head.setItemMeta(sm);button(m,i-start,head,()->go(p,new View("SHOP",l.seller(),0,0,"",v)));}
            paging(p,m,v,rows.size());
        }else if(v.type.equals("TYPES")){
            Map<String,Long> types=new TreeMap<>();for(var l:store.all())if(l.state().equals("ACTIVE"))types.merge(l.material(),1L,Long::sum);List<String> keys=new ArrayList<>(types.keySet());int start=pageStart(v.page,keys.size());for(int i=start;i<Math.min(start+pageSize(),keys.size());i++){String mat=keys.get(i);button(m,i-start,icon(Material.valueOf(mat),"&e"+mat,"&7"+types.get(mat)+" listing","&7Klik untuk memilih penjual"),()->go(p,new View("MATERIAL",mat,0,0,"",v)));}paging(p,m,v,keys.size());
        }else if(v.type.equals("BALANCES")){
            var balances=store.balances(p.getUniqueId().toString());int slot=0;for(var entry:balances.entrySet())if(slot<45){String id=entry.getKey();button(m,slot++,icon(Material.GOLD_INGOT,"&6"+format(id,entry.getValue()),"&7Klik untuk tarik seluruh saldo ini"),()->confirmPayout(p,id,v));}
        }else{
            List<Store.Listing> rows=new ArrayList<>();for(var l:store.all()){
                boolean ok=switch(v.type){
                    case "OWN"->l.state().equals("ACTIVE")&&l.seller().equals(p.getUniqueId().toString());
                    case "CLAIMS"->l.state().equals("CLAIMABLE")&&Objects.equals(l.holder(),p.getUniqueId().toString());
                    case "SHOP"->l.state().equals("ACTIVE")&&l.seller().equals(v.key);
                    case "MATERIAL"->l.state().equals("ACTIVE")&&l.material().equals(v.key);
                    case "CATEGORY"->l.state().equals("ACTIVE")&&category(l.material()).equals(v.key);
                    case "SEARCH"->l.state().equals("ACTIVE")&&searchText(l).contains(v.key.toLowerCase(Locale.ROOT));
                    default->false;};
                if(ok&&(v.currency.isEmpty()||l.currency().equals(v.currency)))rows.add(l);
            }
            Comparator<Store.Listing> cmp=switch(v.sort){case 1->Comparator.comparing(Store.Listing::currency).thenComparingLong(Store.Listing::price);case 2->Comparator.comparing(Store.Listing::currency).thenComparing(Comparator.comparingLong(Store.Listing::price).reversed());case 3->Comparator.comparingLong(Store.Listing::expires);case 4->Comparator.comparing(Store.Listing::label,String.CASE_INSENSITIVE_ORDER);default->Comparator.comparingLong(Store.Listing::created).reversed();};rows.sort(cmp);
            int start=pageStart(v.page,rows.size());int end=Math.min(start+pageSize(),rows.size());
            Map<Long,byte[]> payloads=store.itemPayloads(rows.subList(start,end).stream().map(Store.Listing::id).toList());
            for(int i=start;i<end;i++){
                var l=rows.get(i);ItemStack display=ItemStack.deserializeBytes(payloads.get(l.id())).clone();ItemMeta meta=display.getItemMeta();List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();lore.add(color("&8--------------------"));lore.add(color("&7Penjual: &f"+l.name()));lore.add(color("&7Harga TOTAL: &e"+format(l.currency(),l.price())));lore.add(color("&7Sisa: &f"+Math.max(0,(l.expires()-System.currentTimeMillis())/60000)+" menit"));lore.add(color(v.type.equals("CLAIMS")?"&aKlik untuk ambil item":l.seller().equals(p.getUniqueId().toString())?"&cKlik untuk batalkan listing":"&aKlik untuk konfirmasi pembelian"));meta.setLore(lore);display.setItemMeta(meta);button(m,i-start,display,()->{if(v.type.equals("CLAIMS"))safe(p,()->claim(p,l.id(),v));else confirmation(p,l.id(),v);});
            }paging(p,m,v,rows.size());
            if(!v.type.equals("CLAIMS")){
                String[] sorts={"Terbaru","Termurah (per mata uang)","Termahal (per mata uang)","Segera kedaluwarsa","Nama item"};button(m,48,icon(Material.HOPPER,"&bFilter: "+sorts[v.sort],"&7Klik untuk urutan berikutnya"),()->go(p,new View(v.type,v.key,0,(v.sort+1)%5,v.currency,v.parent)));
                button(m,50,icon(Material.GOLD_NUGGET,"&eMata uang: "+(v.currency.isEmpty()?"Semua":v.currency)),()->{List<String> keys=new ArrayList<>();keys.add("");keys.addAll(currencies.keySet());go(p,new View(v.type,v.key,0,v.sort,keys.get((keys.indexOf(v.currency)+1)%keys.size()),v.parent));});
            }
            if(v.type.equals("OWN")){button(m,51,icon(Material.EMERALD,"&aJual item di tangan","&7Siapkan item di tangan utama"),()->prompt(p,"SELL",v));button(m,52,icon(Material.GOLD_INGOT,"&6Saldo Hasil Penjualan","&7Klik untuk tarik saldo"),()->go(p,new View("BALANCES","",0,0,"",v)));}
        }
        button(m,49,icon(Material.BOOK,"&fInfo","&7Halaman: "+(v.page+1),"&7Harga adalah total satu listing","&7Item diterima lewat Unclaimed Items","&7Kapasitas jual: "+capacity(p),"&7/pmarket sell <harga> [currency]"),()->msg(p,"info",Map.of("capacity",Integer.toString(capacity(p)))));
        button(m,45,ui("back",Material.ARROW,"&cBack"),()->go(p,v.parent==null?home():v.parent));
        p.openInventory(m.inv);
    }
    private int pageSize(){return getConfig().getInt("capacity.slots-per-page",45);}
    private int pageStart(int page,int size){return Math.max(0,Math.min(page,Math.max(0,(size-1)/pageSize())))*pageSize();}
    private void paging(Player p,Menu m,View v,int size){int page=pageStart(v.page,size)/pageSize();if(page>0)button(m,46,ui("previous",Material.ARROW,"&ePrevious"),()->go(p,new View(v.type,v.key,page-1,v.sort,v.currency,v.parent)));if((page+1)*pageSize()<size)button(m,53,ui("next",Material.ARROW,"&eNext"),()->go(p,new View(v.type,v.key,page+1,v.sort,v.currency,v.parent)));if(size==0)m.inv.setItem(22,icon(Material.BARRIER,"&7Belum ada item / shop"));}
    private void go(Player p,View v){safe(p,()->open(p,v));}
    private String format(String currency,long amount){var c=currencies.get(currency);return c==null?amount+" minor units ("+currency+")":c.format(amount);}
    private String category(String mat){var cats=getConfig().getConfigurationSection("categories");if(cats!=null)for(String cat:cats.getKeys(false))for(String pattern:cats.getStringList(cat)){String regex=java.util.regex.Pattern.quote(pattern).replace("*","\\E.*\\Q");if(mat.matches(regex))return cat;}return "Lainnya";}
    private String searchText(Store.Listing l){return l.search();}
    private String searchable(ItemStack item){String text=item.getType().name()+" "+item.getType().name().replace('_',' ')+" "+itemName(item);var m=item.getItemMeta();if(m!=null&&m.hasLore())text+=" "+String.join(" ",m.getLore());return ChatColor.stripColor(text).toLowerCase(Locale.ROOT);}
    private void prompt(Player p,String type,View back){p.closeInventory();prompts.put(p.getUniqueId(),new Prompt(type,back,System.currentTimeMillis()+60000));msg(p,type.equals("SELL")?"prompt-sell":type.equals("ITEM")?"prompt-item":"prompt-shop",Map.of());}
    @EventHandler(priority=EventPriority.LOWEST) public void chat(AsyncPlayerChatEvent e){Prompt q=prompts.remove(e.getPlayer().getUniqueId());if(q==null)return;e.setCancelled(true);String text=e.getMessage().trim();Bukkit.getScheduler().runTask(this,()->{Player p=e.getPlayer();if(!p.isOnline())return;if(System.currentTimeMillis()>q.expires||text.equalsIgnoreCase("cancel")){go(p,q.back);return;}if(!p.hasPermission("hellnah.pmarket.use"))return;if(text.length()>80){msg(p,"invalid-input",Map.of());return;}safe(p,()->{if(q.type.equals("SELL")){String[] args=text.split("\\s+");startSale(p,args[0],args.length>1?args[1]:getConfig().getString("default-currency","money"),q.back);}else open(p,new View(q.type.equals("ITEM")?"SEARCH":"SHOPSEARCH",text,0,0,"",q.back));});});}
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(this,()->{Player p=e.getPlayer();if(!p.isOnline()||!p.hasPermission("hellnah.pmarket.use"))return;safe(p,()->{store.expire(System.currentTimeMillis());long count=store.all().stream().filter(l->l.state().equals("CLAIMABLE")&&p.getUniqueId().toString().equals(l.holder())).count();if(count>0)msg(p,"unclaimed-reminder",Map.of("count",Long.toString(count)));if(!store.balances(p.getUniqueId().toString()).isEmpty())msg(p,"balance-reminder",Map.of());});},40);}
    @EventHandler public void quit(PlayerQuitEvent e){prompts.remove(e.getPlayer().getUniqueId());clicks.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||e.getRawSlot()<0||e.getRawSlot()>=54)return;if(e.getClick()!=ClickType.LEFT&&e.getClick()!=ClickType.RIGHT)return;long now=System.currentTimeMillis();if(now-clicks.getOrDefault(p.getUniqueId(),0L)<180)return;clicks.put(p.getUniqueId(),now);if(!p.hasPermission("hellnah.pmarket.use")){p.closeInventory();return;}Runnable a=m.actions.get(e.getRawSlot());if(a!=null)Bukkit.getScheduler().runTask(this,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory()==m.inv)a.run();});}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    private void startSale(Player p,String input,String id,View back)throws Exception{
        if(!p.hasPermission("hellnah.pmarket.sell")){msg(p,"no-permission",Map.of());return;}if(!survival(p))return;
        var currency=currencies.get(id);if(currency==null){msg(p,"invalid-currency",Map.of("currencies",String.join(", ",currencies.keySet())));return;}long price;try{price=currency.parse(input);}catch(Exception e){msg(p,"invalid-price",Map.of());return;}
        if(price<getConfig().getLong("listing.min-price-minor",1)||price>getConfig().getLong("listing.max-price-minor",100000000000L)||(getConfig().getString("currencies."+id+".type","").equalsIgnoreCase("PLAYERPOINTS")&&price>Integer.MAX_VALUE)){msg(p,"invalid-price",Map.of());return;}
        int slot=p.getInventory().getHeldItemSlot();ItemStack held=p.getInventory().getItem(slot);if(held==null||held.getType().isAir()){msg(p,"empty-hand",Map.of());return;}ItemStack snapshot=held.clone();if(getConfig().getStringList("listing.blocked-materials").contains(held.getType().name())){msg(p,"blocked-item",Map.of());return;}
        if(snapshot.serializeAsBytes().length>getConfig().getInt("security.max-item-bytes",1048576)){msg(p,"blocked-item",Map.of());return;}
        if(store.count(p.getUniqueId().toString())>=capacity(p)){msg(p,"full-market",Map.of());return;}
        Menu m=menu(title("confirm","Konfirmasi Market"));m.inv.setItem(22,snapshot);button(m,30,icon(Material.LIME_CONCRETE,"&aKonfirmasi Jual", "&7Harga total: "+currency.format(price),"&7Jumlah: "+snapshot.getAmount()),()->safe(p,()->{
            if(!p.hasPermission("hellnah.pmarket.sell")||!survival(p))return;
            if(!snapshot.equals(p.getInventory().getItem(slot))){msg(p,"item-changed",Map.of());return;}
            if(store.count(p.getUniqueId().toString())>=capacity(p)){msg(p,"full-market",Map.of());return;}
            long expires=System.currentTimeMillis()+getConfig().getInt("listing.expiry-hours",72)*3600000L;
            long listing=store.prepareListing(p.getUniqueId().toString(),p.getName(),snapshot.serializeAsBytes(),snapshot.getType().name(),itemName(snapshot),searchable(snapshot),price,id,expires,capacity(p));
            p.getInventory().setItem(slot,null);p.saveData();
            if(!store.transition(listing,"LIST_PREP","ACTIVE",null))throw new SQLException("Listing activation failed");store.audit(p.getUniqueId().toString(),"SELL",Long.toString(listing));msg(p,"listed",Map.of("item",itemName(snapshot),"price",currency.format(price)));open(p,back);
        }));button(m,32,icon(Material.RED_CONCRETE,"&cBatal / Back"),()->go(p,back));p.openInventory(m.inv);
    }
    private String itemName(ItemStack item){return item.hasItemMeta()&&item.getItemMeta().hasDisplayName()?ChatColor.stripColor(item.getItemMeta().getDisplayName()):item.getType().name();}
    private void checkCurrencies(Map<String,Currencies.Currency> defs)throws SQLException {store.requireCurrencies(defs.keySet());for(var c:defs.values())store.currencyDefinition(c.id(),c.decimals(),getConfig().getString("currencies."+c.id()+".type","VAULT").toUpperCase(Locale.ROOT));}
    private long net(long price){return java.math.BigDecimal.valueOf(price).multiply(java.math.BigDecimal.valueOf(100-getConfig().getDouble("listing.tax-percent",0))).divide(java.math.BigDecimal.valueOf(100),0,java.math.RoundingMode.DOWN).longValueExact();}
    private void confirmation(Player p,long id,View back){safe(p,()->{var l=store.get(id);if(l==null||!l.state().equals("ACTIVE")){msg(p,"unavailable",Map.of());go(p,back);return;}boolean own=l.seller().equals(p.getUniqueId().toString());Menu m=menu(title("confirm","Konfirmasi Market"));m.inv.setItem(22,ItemStack.deserializeBytes(l.item()));button(m,30,icon(Material.LIME_CONCRETE,own?"&cBatalkan Listing":"&aBeli Item","&7Harga total: "+format(l.currency(),l.price()),"&7Penjual: "+l.name()),()->safe(p,()->{if(own){if(store.cancel(id,p.getUniqueId().toString()))msg(p,"cancelled",Map.of());else msg(p,"unavailable",Map.of());}else buy(p,id);open(p,back);}));button(m,32,icon(Material.RED_CONCRETE,"&cBatal / Back"),()->go(p,back));p.openInventory(m.inv);});}
    private void buy(Player p,long id)throws Exception{
        if(!survival(p))return;var l=store.get(id);if(l==null||l.seller().equals(p.getUniqueId().toString())||!l.state().equals("ACTIVE")||l.expires()<=System.currentTimeMillis()){msg(p,"unavailable",Map.of());return;}
        var currency=currencies.get(l.currency());if(currency==null){msg(p,"invalid-currency",Map.of("currencies",String.join(",",currencies.keySet())));return;}
        if(!store.reserve(id,p.getUniqueId().toString(),System.currentTimeMillis(),net(l.price()))){msg(p,"unavailable",Map.of());return;}
        boolean ok;try{ok=currency.backend().withdraw(p,l.price());}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"BUY_PREP #"+id+" ambiguous economy result, do not retry automatically",e);msg(p,"quarantined",Map.of());return;}
        if(!ok){store.transition(id,"BUY_PREP","ACTIVE",null);msg(p,"insufficient",Map.of());return;}
        store.completePurchase(id);msg(p,"bought",Map.of("item",l.label(),"price",currency.format(l.price())));
        Player seller=Bukkit.getPlayer(UUID.fromString(l.seller()));if(seller!=null)msg(seller,"sold",Map.of("item",l.label(),"buyer",p.getName(),"price",currency.format(store.get(id).net())));
    }
    private void claim(Player p,long id,View back)throws Exception{
        if(!survival(p))return;var l=store.get(id);if(l==null||!l.state().equals("CLAIMABLE")||!p.getUniqueId().toString().equals(l.holder())){msg(p,"unavailable",Map.of());return;}ItemStack item=ItemStack.deserializeBytes(l.item());
        // Reserve one fully empty storage slot. Never drop overflow into the world.
        int slot=p.getInventory().firstEmpty();if(slot<0||slot>=36){msg(p,"inventory-full",Map.of());return;}
        if(!store.transition(id,"CLAIMABLE","CLAIM_PREP",l.holder())){msg(p,"unavailable",Map.of());return;}
        p.getInventory().setItem(slot,item);p.saveData();if(!store.transition(id,"CLAIM_PREP","DONE",l.holder()))throw new SQLException("Claim finalize failed");store.audit(p.getUniqueId().toString(),"CLAIM",Long.toString(id));msg(p,"claimed",Map.of("item",l.label()));open(p,back);
    }
    private void confirmPayout(Player p,String currency,View back){safe(p,()->{long amount=store.balances(p.getUniqueId().toString()).getOrDefault(currency,0L);if(amount==0)return;Menu m=menu(title("confirm","Konfirmasi Market"));button(m,30,icon(Material.LIME_CONCRETE,"&aTarik Saldo","&7"+format(currency,amount)),()->safe(p,()->{var c=currencies.get(currency);if(c==null){msg(p,"error",Map.of());return;}long current=store.balances(p.getUniqueId().toString()).getOrDefault(currency,0L);if(current<=0){go(p,back);return;}if(getConfig().getString("currencies."+currency+".type","").equalsIgnoreCase("PLAYERPOINTS")&&current>Integer.MAX_VALUE){msg(p,"error",Map.of());return;}long id=store.preparePayout(p.getUniqueId().toString(),currency);var q=store.payout(id);boolean ok;try{ok=c.backend().deposit(p,q.amount());}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"PAY_PREP payout:"+id+" ambiguous result",e);msg(p,"quarantined",Map.of());return;}store.finishPayout(id,ok);msg(p,ok?"paid":"payout-failed",Map.of("price",format(currency,q.amount())));open(p,back);}));button(m,32,icon(Material.RED_CONCRETE,"&cBatal / Back"),()->go(p,back));p.openInventory(m.inv);});}
}
