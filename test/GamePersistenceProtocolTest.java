package memorygame.test;

import memorygame.server.GameServer;
import java.nio.file.*;
import java.sql.*;

public final class GamePersistenceProtocolTest {
    public static void main(String[] args) throws Exception {
        boolean mysql=!"file".equals(System.getenv("MEMORY_GAME_STORAGE"));
        Path dir=Files.createTempDirectory("game-protocol-");
        GameServer server=new GameServer(ProfileTest.PORT,dir);
        if(mysql) try(var c=connection();var s=c.createStatement()) {
            s.executeUpdate("UPDATE products SET price=1,name='Giá từ database' WHERE product_id='theme_ocean'");
        }
        Thread thread=ProfileTest.start(server);
        try { FullGameReplayTest.main(mysql?new String[]{String.valueOf(ProfileTest.PORT),"database"}:new String[]{String.valueOf(ProfileTest.PORT)}); }
        finally {server.close();thread.join(2000);}
        if(mysql) try(var c=connection();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT COUNT(*) FROM matches WHERE status='COMPLETED'")) {r.next();if(r.getInt(1)!=2)throw new AssertionError("Two completed matches required");}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM match_results WHERE score IS NOT NULL")) {r.next();if(r.getInt(1)!=4)throw new AssertionError("Four results required");}
            try(var r=s.executeQuery("SELECT SUM(forfeited) FROM match_results")) {r.next();if(r.getInt(1)!=1)throw new AssertionError("Forfeit not persisted");}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM users WHERE games=2")) {r.next();if(r.getInt(1)!=2)throw new AssertionError("Incorrect game count");}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM user_items WHERE product_id='theme_ocean'")) {r.next();if(r.getInt(1)!=1)throw new AssertionError("Purchase not persisted");}
        }
        System.out.println("GAME_PROTOCOL_OK complete replay forfeit history shop");
    }
    static Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("MEMORY_GAME_DB_URL"),System.getenv("MEMORY_GAME_DB_USER"),System.getenv("MEMORY_GAME_DB_PASSWORD"));
    }
}
