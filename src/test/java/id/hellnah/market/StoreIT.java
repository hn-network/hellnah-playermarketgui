package id.hellnah.market;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreIT {
    Store s;
    DatabaseSettings settings;
    static String env(String key,String fallback){return System.getenv().getOrDefault(key,fallback);}
    @BeforeEach void setup()throws Exception{
        String database=System.getenv("PMARKET_TEST_DATABASE");
        assertNotNull(database,"mysql-it requires PMARKET_TEST_DATABASE; use a disposable test database");
        settings=new DatabaseSettings(env("PMARKET_TEST_HOST","127.0.0.1"),Integer.parseInt(env("PMARKET_TEST_PORT","3306")),
            database,env("PMARKET_TEST_USER","root"),env("PMARKET_TEST_PASSWORD",""),
            "it_"+UUID.randomUUID().toString().replace("-","").substring(0,20)+"_", "DISABLED",true,3000,3000);
        s=new Store(settings);
    }
    @AfterEach void close()throws Exception{
        if(s!=null)s.close();
        if(settings==null)return;
        Driver driver=(Driver)Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
        try(var connection=driver.connect(settings.url(),settings.properties());var statement=connection.createStatement()){
            // Only this test's unique, validated prefix is removed. Never drop a database.
            for(String table:List.of("listings","currency_defs","balances","payouts","audit"))
                statement.execute("DROP TABLE IF EXISTS `"+settings.prefix()+table+"`");
        }
    }
    long listing(String seller,long expires)throws Exception {return s.prepareListing(seller,"Player",new byte[]{1,2,3,0,-1},"DIAMOND","Rare","diamond rare",10000,"money",expires,45);}
    long active()throws Exception{long id=listing("seller",System.currentTimeMillis()+1000000);assertTrue(s.transition(id,"LIST_PREP","ACTIVE",null));return id;}
    @Test void twoBuyersCannotReserveSameListing()throws Exception{long id=active();assertTrue(s.reserve(id,"buyerA",System.currentTimeMillis(),9500));assertFalse(s.reserve(id,"buyerB",System.currentTimeMillis(),9500));assertEquals("buyerA",s.get(id).holder());}
    @Test void selfPurchaseAndExpiredPurchaseRejected()throws Exception{long id=active();assertFalse(s.reserve(id,"seller",System.currentTimeMillis(),10000));long ex=listing("other",1);s.transition(ex,"LIST_PREP","ACTIVE",null);assertFalse(s.reserve(ex,"buyer",2,10000));}
    @Test void saleCreditsExactlyOnceAndKeepsNetSnapshot()throws Exception{long id=active();s.reserve(id,"buyer",System.currentTimeMillis(),9100);s.completePurchase(id);assertEquals("CLAIMABLE",s.get(id).state());assertEquals("buyer",s.get(id).holder());assertEquals(9100L,s.balances("seller").get("money"));assertThrows(java.sql.SQLException.class,()->s.completePurchase(id));assertEquals(9100L,s.balances("seller").get("money"));}
    @Test void expiryReturnsOnlyActiveItemsToSeller()throws Exception{long ex=listing("seller",1);s.transition(ex,"LIST_PREP","ACTIVE",null);long prep=listing("seller",1);s.expire(5);assertEquals("CLAIMABLE",s.get(ex).state());assertEquals("seller",s.get(ex).holder());assertEquals("LIST_PREP",s.get(prep).state());}
    @Test void cancelRequiresOwnerAndCannotTakeBuyerItem()throws Exception{long id=active();assertFalse(s.cancel(id,"attacker"));s.reserve(id,"buyer",System.currentTimeMillis(),10000);assertFalse(s.cancel(id,"seller"));s.completePurchase(id);assertFalse(s.cancel(id,"seller"));assertEquals("buyer",s.get(id).holder());}
    @Test void cancelAndClaimTransitionOnlyOnce()throws Exception{long id=active();assertTrue(s.cancel(id,"seller"));assertFalse(s.cancel(id,"seller"));assertTrue(s.transition(id,"CLAIMABLE","CLAIM_PREP","seller"));assertFalse(s.transition(id,"CLAIMABLE","CLAIM_PREP","seller"));assertTrue(s.transition(id,"CLAIM_PREP","DONE","seller"));}
    @Test void capacityIncludesInFlightListings()throws Exception{for(int i=0;i<45;i++)listing("seller",100000);assertEquals(45,s.count("seller"));assertThrows(java.sql.SQLException.class,()->listing("seller",100000));assertEquals(45,s.count("seller"));}
    @Test void failedWithdrawalCanReleaseReservation()throws Exception{long id=active();s.reserve(id,"buyer",System.currentTimeMillis(),10000);assertTrue(s.transition(id,"BUY_PREP","ACTIVE",null));assertTrue(s.reserve(id,"buyerB",System.currentTimeMillis(),10000));assertTrue(s.balances("seller").isEmpty());}
    void balance()throws Exception{long id=active();s.reserve(id,"buyer",System.currentTimeMillis(),9000);s.completePurchase(id);}
    @Test void payoutEscrowsAndCannotBeReplayed()throws Exception{balance();long p=s.preparePayout("seller","money");assertTrue(s.balances("seller").isEmpty());assertThrows(java.sql.SQLException.class,()->s.preparePayout("seller","money"));s.finishPayout(p,true);assertThrows(java.sql.SQLException.class,()->s.finishPayout(p,false));assertTrue(s.balances("seller").isEmpty());}
    @Test void failedPayoutReturnsBalanceOnce()throws Exception{balance();long p=s.preparePayout("seller","money");s.finishPayout(p,false);assertEquals(9000L,s.balances("seller").get("money"));assertThrows(java.sql.SQLException.class,()->s.finishPayout(p,false));assertEquals(9000L,s.balances("seller").get("money"));}
    @Test void pendingOperationsSurviveRestartWithoutAutoReplay()throws Exception{long list=listing("seller",10000);long buy=active();s.reserve(buy,"buyer",System.currentTimeMillis(),8500);balance();long pay=s.preparePayout("seller","money");long claim=active();s.cancel(claim,"seller");s.transition(claim,"CLAIMABLE","CLAIM_PREP","seller");s.close();s=new Store(settings);s.expire(System.currentTimeMillis());assertEquals("LIST_PREP",s.get(list).state());assertEquals("BUY_PREP",s.get(buy).state());assertEquals("CLAIM_PREP",s.get(claim).state());assertEquals("PAY_PREP",s.payout(pay).state());assertTrue(s.balances("seller").isEmpty());}
    @Test void secondServerUsingSameTablesIsRejected()throws Exception{assertThrows(java.sql.SQLException.class,()->new Store(settings));assertNotNull(s.all());}
    @Test void oneQueryLoadsOnlyRequestedPagePayloads()throws Exception{long one=active();long two=active();var items=s.itemPayloads(List.of(one));assertEquals(Set.of(one),items.keySet());assertNotNull(items.get(one));assertFalse(items.containsKey(two));}
    @Test void serializedItemBytesRoundTripExactly()throws Exception{long id=active();assertArrayEquals(new byte[]{1,2,3,0,-1},s.get(id).item());}
    @Test void browseUsesMetadataWithoutLoadingItemBlobs()throws Exception{long id=active();var l=s.all().getFirst();assertNull(l.item());assertEquals("diamond rare",l.search());assertNotNull(s.get(id).item());}
    @Test void quotesInPlayerNameCannotInjectSql()throws Exception{String owner="x'; DROP TABLE listings;--";long id=listing(owner,10000);assertEquals(owner,s.get(id).seller());assertEquals(1,s.count(owner));assertNotNull(s.get(id));}
    @Test void currencyScaleAndTypeAreImmutable()throws Exception{s.currencyDefinition("money",2,"VAULT");s.currencyDefinition("money",2,"VAULT");assertThrows(IllegalArgumentException.class,()->s.currencyDefinition("money",0,"VAULT"));assertThrows(IllegalArgumentException.class,()->s.currencyDefinition("money",2,"SERVICE"));active();assertThrows(IllegalArgumentException.class,()->s.requireCurrencies(Set.of("points")));s.requireCurrencies(Set.of("money"));}
}
