package memorygame.server;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Run only against an isolated schema via DatabaseFlowsTest. */
public final class GameDatabaseTest {
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static long number(Connection c,String sql) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)) { r.next();return r.getLong(1); }
    }
    static Map<String,Object> result(String name,int score,int place,boolean winner,boolean forfeit,int delta,int coins,boolean credit) {
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("username",name);r.put("score",score);r.put("placement",place);r.put("winner",winner);r.put("forfeited",forfeit);
        r.put("ratingDelta",delta);r.put("coinReward",coins);r.put("winCredit",credit);r.put("resultText","27/10/2026 01:00 | test | "+score+" cặp | Hạng "+place+" | Xếp hạng "+delta+" | +"+coins+" xu");return r;
    }
    public static void main(String[] args) throws Exception {
        // Bootstrap old schema before constructing DatabaseStore: exercise actual upgrade.
        try(Connection c=DriverManager.getConnection(System.getenv("MEMORY_GAME_DB_URL"),System.getenv("MEMORY_GAME_DB_USER"),System.getenv("MEMORY_GAME_DB_PASSWORD"));var s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE users(username VARCHAR(16) PRIMARY KEY,password_hash CHAR(64),rating INT DEFAULT 0,wins INT DEFAULT 0,losses INT DEFAULT 0,games INT DEFAULT 0,coins INT DEFAULT 0,equipped_theme VARCHAR(64) DEFAULT 'theme_classic',equipped_title VARCHAR(64) DEFAULT '') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            s.executeUpdate("CREATE TABLE user_items(username VARCHAR(16),product_id VARCHAR(64),purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(username,product_id),FOREIGN KEY(username) REFERENCES users(username)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            s.executeUpdate("CREATE TABLE match_history(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(16),history_order INT,result_text VARCHAR(500),played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(username,history_order)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            s.executeUpdate("INSERT INTO users(username,password_hash,rating,wins,losses,games,coins) VALUES ('old','hash',8,2,3,5,500),('other','hash',0,0,0,0,0)");
            s.executeUpdate("INSERT INTO user_items VALUES ('old','unknown_old','2020-01-01 00:00:00')");
            s.executeUpdate("INSERT INTO match_history(username,history_order,result_text) VALUES ('old',0,'nguyên văn lịch sử cũ'),('old',1,'dòng thứ hai')");
        }
        DatabaseStore db=new DatabaseStore();
        try(Connection c=db.open();var s=c.createStatement()) {
            check(db.history("old").equals(List.of("nguyên văn lịch sử cũ","dòng thứ hai")),"legacy text/order");
            check(number(c,"SELECT COUNT(*) FROM match_results") == 2,"legacy migrated");
            check(number(c,"SELECT coins FROM users WHERE username='old'")==500,"migration rewarded old match");
            check(number(c,"SELECT active FROM products WHERE product_id='unknown_old'")==0,"orphan item not retained");
            s.executeUpdate("UPDATE products SET price=75,name='Tên từ database' WHERE product_id='theme_ocean'");
            new DatabaseStore();
            check(number(c,"SELECT COUNT(*) FROM match_results")==2,"migration duplicated");
            check(db.products(true).stream().anyMatch(p->p.name().equals("Tên từ database") && p.price()==75),"database catalog edit overwritten");
            check(number(c,"SELECT YEAR(purchased_at) FROM user_items WHERE product_id='unknown_old'")==2020,"purchase timestamp changed");
            var snapshot=db.loadUsers();
            ExecutorService pool=Executors.newFixedThreadPool(2);
            try {
                var a=pool.submit(()->db.purchase("old","theme_ocean"));var b=pool.submit(()->db.purchase("old","theme_ocean"));
                check(a.get().isEmpty() != b.get().isEmpty(),"duplicate purchase accepted");
            } finally {pool.shutdownNow();}
            check(number(c,"SELECT coins FROM users WHERE username='old'")==425,"double debit");
            db.saveUsers(snapshot);
            check(number(c,"SELECT coins FROM users WHERE username='old'")==425,"stale snapshot overwrote balance");
            check(!db.purchase("other","theme_gold").isEmpty(),"insufficient funds");
            s.executeUpdate("UPDATE products SET active=FALSE WHERE product_id='title_memory'");
            check(!db.purchase("old","title_memory").isEmpty(),"inactive product");
            s.executeUpdate("CREATE TRIGGER fail_item BEFORE INSERT ON user_items FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure'");
            try { db.purchase("old","theme_gold");throw new AssertionError("injected purchase failure not raised"); } catch(IllegalStateException expected) { }
            s.executeUpdate("DROP TRIGGER fail_item");
            check(number(c,"SELECT coins FROM users WHERE username='old'")==425,"purchase rollback lost coins");
            check(number(c,"SELECT COUNT(*) FROM user_items WHERE product_id='theme_gold'")==0,"purchase rollback leaked item");
            List<Map<String,Object>> results=List.of(result("old",7,1,true,false,3,50,true),result("other",1,2,false,true,-1,10,false));
            try { db.beginMatch("bad_start","ROOM",4,"FRUIT",1,List.of("old","missing"));throw new AssertionError("invalid participant accepted"); }
            catch(IllegalStateException expected) { }
            check(number(c,"SELECT COUNT(*) FROM matches WHERE match_id='bad_start'")==0,"start rollback left orphan match");
            db.beginMatch("first","ROOM",4,"FRUIT",1,List.of("old","other"));
            s.executeUpdate("CREATE TRIGGER fail_result BEFORE UPDATE ON match_results FOR EACH ROW BEGIN IF NEW.username='other' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure'; END IF; END");
            try { db.completeMatch("first","Đủ cặp",false,2,results);throw new AssertionError("injected settlement failure not raised"); } catch(IllegalStateException expected) { }
            s.executeUpdate("DROP TRIGGER fail_result");
            check(number(c,"SELECT coins FROM users WHERE username='old'")==425,"partial reward rollback failed");
            check(number(c,"SELECT COUNT(*) FROM match_results WHERE match_id='first' AND score IS NOT NULL")==0,"partial results committed");
            ExecutorService finish=Executors.newFixedThreadPool(2);
            try {
                var a=finish.submit(()->db.completeMatch("first","Đủ cặp",false,2,results));
                var b=finish.submit(()->db.completeMatch("first","Đủ cặp",false,2,results));a.get();b.get();
            } finally {finish.shutdownNow();}
            check(number(c,"SELECT coins FROM users WHERE username='old'")==475,"duplicate reward");
            check(number(c,"SELECT wins FROM users WHERE username='old'")==3,"win credit");
            check(number(c,"SELECT rating FROM users WHERE username='other'")==0,"rating floor");
            check(number(c,"SELECT rating_applied FROM match_results WHERE match_id='first' AND username='other'")==0,"applied delta");
            check(number(c,"SELECT forfeited FROM match_results WHERE match_id='first' AND username='other'")==1,"forfeit flag");
            db.beginMatch("replay","ROOM",4,"FRUIT",3,List.of("old","other"));
            db.completeMatch("replay","Hoà",false,4,List.of(result("old",4,1,true,false,1,50,false),result("other",4,1,true,false,1,50,false)));
            check(number(c,"SELECT wins FROM users WHERE username='old'")==3,"draw changed win rule");
            check(number(c,"SELECT games FROM users WHERE username='old'")==7,"replay game count");
            new DatabaseStore().completeMatch("first","duplicate restart",false,5,results);
            check(number(c,"SELECT coins FROM users WHERE username='old'")==525,"restart duplicated reward");
            check(db.history("old").size()==4,"new history missing");
            check(number(c,"SELECT COUNT(*) FROM match_history")==2,"legacy table mutated");
            var imported=new GameServer.UserRecord("imported","hash");
            imported.coins=12;imported.rating=5;imported.history.add("lịch sử từ state.bin");
            db.saveUsers(Map.of("imported",imported));
            check(db.history("imported").equals(imported.history),"file history import");
            check(number(c,"SELECT coins FROM users WHERE username='imported'")==12,"file stats import");
            new DatabaseStore();
            check(db.history("imported").size()==1,"file history imported twice");
            try {s.executeUpdate("INSERT INTO user_items(username,product_id) VALUES('old','missing')");throw new AssertionError("product FK missing");}catch(SQLException expected) { }
        }
        DatabaseIntegrityChecks.run(db);
        System.out.println("GAME_DATABASE_OK migration catalog purchaseConcurrency rollback idempotentRewards replay draw forfeit history foreignKeys");
    }
}
