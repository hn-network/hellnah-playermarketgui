package id.hellnah.market;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DatabaseSettingsTest {
    DatabaseSettings settings(String host,String db,String prefix){return new DatabaseSettings(host,3306,db,"market","secret",prefix,"REQUIRED",false,3000,3000);}
    @Test void refusesSqlInTablePrefix(){assertThrows(IllegalArgumentException.class,()->settings("localhost","market","x`;DROP TABLE players;--"));}
    @Test void refusesJdbcPropertyInjection(){assertThrows(IllegalArgumentException.class,()->settings("localhost?autoReconnect=true","market","hnpm_"));assertThrows(IllegalArgumentException.class,()->settings("localhost","market?allowMultiQueries=true","hnpm_"));}
    @Test void credentialsAreNotInUrlOrDescription(){var s=settings("localhost","market","hnpm_");assertFalse(s.url().contains("secret"));assertFalse(s.toString().contains("secret"));assertEquals("secret",s.properties().getProperty("password"));}
    @Test void noAutomaticReconnectOrMultiQueries(){var s=settings("localhost","market","hnpm_");assertEquals("false",s.properties().getProperty("autoReconnect"));assertEquals("false",s.properties().getProperty("allowMultiQueries"));}
    @Test void ipv6HostIsBracketed(){assertEquals("jdbc:mysql://[::1]:3306/market",settings("::1","market","hnpm_").url());}
}
