package memorygame.test;

import memorygame.server.GameServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import memorygame.common.Protocol.*;

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
        if(mysql) {
            String buyer; int coins, rating;
            try(var c=connection();var s=c.createStatement();var r=s.executeQuery("SELECT u.username,u.coins,u.rating FROM users u JOIN user_items i ON i.username=u.username WHERE i.product_id='theme_ocean'")) {
                if(!r.next())throw new AssertionError("Missing buyer");
                buyer=r.getString(1);coins=r.getInt(2);rating=r.getInt(3);
            }
            server=new GameServer(ProfileTest.PORT,dir);thread=ProfileTest.start(server);
            try(var client=new ProfileTest.Client()) {
                client.send(Message.of(Type.LOGIN).with("username",buyer).with("password","test123"));
                Message login=client.await(Type.LOGIN_OK);
                if(login.integer("coins")!=coins || login.integer("rating")!=rating)throw new AssertionError("Restart lost wallet/rating");
                client.send(Message.of(Type.SHOP_REQUEST));
                List<Map<String,Object>> products=client.await(Type.SHOP_UPDATE).value("products");
                if(products.stream().noneMatch(p->"theme_ocean".equals(p.get("id")) && Boolean.TRUE.equals(p.get("owned"))))
                    throw new AssertionError("Restart lost purchased item");
                client.send(Message.of(Type.HISTORY_REQUEST));
                if(client.await(Type.HISTORY_UPDATE).<List<String>>value("history").size()!=2)throw new AssertionError("Restart lost match history");
            }finally{server.close();thread.join(2000);}
        }
        System.out.println("GAME_PROTOCOL_OK complete replay forfeit history shop restartWalletRatingItemsHistory");
    }
    static Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("MEMORY_GAME_DB_URL"),System.getenv("MEMORY_GAME_DB_USER"),System.getenv("MEMORY_GAME_DB_PASSWORD"));
    }
}
