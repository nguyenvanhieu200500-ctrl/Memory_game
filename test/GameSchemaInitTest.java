package memorygame.server;

import java.nio.file.*;
import java.sql.*;

public final class GameSchemaInitTest {
    public static void main(String[] args) throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("MEMORY_GAME_DB_URL"),System.getenv("MEMORY_GAME_DB_USER"),System.getenv("MEMORY_GAME_DB_PASSWORD"));var s=c.createStatement()) {
            for(String sql:Files.readString(Path.of("docker/mysql/init.sql")).split(";")) {
                sql=sql.strip();
                if(sql.isEmpty() || sql.startsWith("CREATE DATABASE") || sql.startsWith("USE "))continue;
                s.executeUpdate(sql);
            }
        }
        DatabaseStore db=new DatabaseStore();
        if(db.products(true).size()!=6)throw new AssertionError("SQL catalog differs");
        try(var c=db.open();var r=c.getMetaData().getImportedKeys(c.getCatalog(),null,"user_items")) {
            boolean products=false,users=false;
            while(r.next()){products|=r.getString("PKTABLE_NAME").equals("products");users|=r.getString("PKTABLE_NAME").equals("users");}
            if(!products || !users)throw new AssertionError("SQL relationships");
        }
        DatabaseIntegrityChecks.run(db);
        System.out.println("GAME_SCHEMA_INIT_OK SQL bootstrap automaticUpgrade relations catalog");
    }
}
