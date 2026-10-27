package memorygame.server;

import java.sql.*;
import java.util.*;

final class DatabaseIntegrityChecks {
    static void rejected(Connection c,String sql) throws SQLException {
        try(var s=c.createStatement()) {
            s.executeUpdate(sql);
            throw new AssertionError("Invalid data accepted: "+sql);
        } catch(SQLException e) {
            if(!Set.of(3819,1062,1452,1451,1048).contains(e.getErrorCode())) throw e;
        }
    }
    static void run(DatabaseStore db) throws Exception {
        var a=new GameServer.UserRecord("integrity_a","hash");a.coins=500;
        var b=new GameServer.UserRecord("integrity_b","hash");
        db.saveUsers(Map.of(a.username,a,b.username,b));
        db.beginMatch("integrity","ROOM",4,"FRUIT",10,List.of(a.username,b.username));
        try(var c=db.open();var s=c.createStatement()) {
            for(String sql:List.of(
                "UPDATE users SET coins=-1 WHERE username='integrity_a'",
                "UPDATE users SET rating=-1 WHERE username='integrity_a'",
                "UPDATE products SET price=-1 WHERE product_id='theme_ocean'",
                "UPDATE products SET active=2 WHERE product_id='theme_ocean'",
                "UPDATE match_results SET score=-1 WHERE match_id='integrity'",
                "UPDATE match_results SET coin_reward=-1 WHERE match_id='integrity'",
                "UPDATE match_results SET placement=0 WHERE match_id='integrity'",
                "UPDATE match_results SET winner=2 WHERE match_id='integrity'",
                "UPDATE matches SET status='INVALID' WHERE match_id='integrity'",
                "UPDATE matches SET board_size=5 WHERE match_id='integrity'",
                "UPDATE matches SET player_count=1 WHERE match_id='integrity'",
                "UPDATE matches SET started_at=NULL WHERE match_id='integrity'",
                "UPDATE matches SET ended_at=5 WHERE match_id='integrity'",
                "INSERT INTO match_results(match_id,username) VALUES('integrity','integrity_a')",
                "INSERT INTO match_results(match_id,username) VALUES('missing','integrity_a')",
                "INSERT INTO match_results(match_id,username) VALUES('integrity','missing')"
            )) rejected(c,sql);
            if(!db.purchase(a.username,"theme_ocean").isEmpty()) throw new AssertionError("purchase");
            rejected(c,"INSERT INTO user_items(username,product_id) VALUES('integrity_a','theme_ocean')");
            s.executeUpdate("UPDATE products SET active=FALSE WHERE product_id='theme_ocean'");
            if(db.productsFor(a.username).stream().noneMatch(p->p.id().equals("theme_ocean")))throw new AssertionError("Retired owned item hidden");
            if(db.products(true).stream().anyMatch(p->p.id().equals("theme_ocean")))throw new AssertionError("Retired item sold");
            if(db.purchase(b.username,"theme_ocean").isEmpty())throw new AssertionError("Retired purchase");
            db.updateEquipment(a.username,"theme_ocean",true);
            rejected(c,"DELETE FROM products WHERE product_id='theme_ocean'");
            var stale=db.loadUsers();
            s.executeUpdate("UPDATE users SET password_hash='newhash',avatar='newavatar',equipped_theme='theme_gold',coins=77,rating=9 WHERE username='integrity_a'");
            s.executeUpdate("DELETE FROM user_items WHERE username='integrity_a'");
            db.saveUsers(stale);
            try {db.updateEquipment(a.username,"theme_ocean",true);throw new AssertionError("Stale ownership equipped");}catch(IllegalStateException expected) { }
            try {db.updatePassword(a.username,"hash","oldwrite");throw new AssertionError("Stale password overwrote DB");}catch(IllegalStateException expected) { }
            try(var r=s.executeQuery("SELECT password_hash,avatar,equipped_theme,coins,rating FROM users WHERE username='integrity_a'")) {
                r.next();if(!r.getString(1).equals("newhash")||!r.getString(2).equals("newavatar")||!r.getString(3).equals("theme_gold")||r.getInt(4)!=77||r.getInt(5)!=9)throw new AssertionError("Stale RAM overwrote DB");
            }
            if(GameDatabaseTest.number(c,"SELECT COUNT(*) FROM user_items WHERE username='integrity_a'")!=0)throw new AssertionError("Stale ownership resurrected");
            s.executeUpdate("INSERT INTO match_history(username,history_order,result_text) VALUES('integrity_a',0,'late legacy row')");
            if(!db.history(a.username).contains("late legacy row"))throw new AssertionError("Legacy fallback missing");
            new DatabaseStore();
            if(Collections.frequency(db.history(a.username),"late legacy row")!=1)throw new AssertionError("Migrated history duplicated");
            s.executeUpdate("CREATE TRIGGER fail_interrupt BEFORE UPDATE ON match_results FOR EACH ROW BEGIN IF NEW.username='integrity_b' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected interruption failure'; END IF; END");
            try {db.interruptMatch("integrity",20,"Server dừng",Map.of(a.username,2,b.username,0),Set.of(b.username));throw new AssertionError("Missing failure");}
            catch(IllegalStateException expected) { }
            s.executeUpdate("DROP TRIGGER fail_interrupt");
            if(GameDatabaseTest.number(c,"SELECT COUNT(*) FROM match_results WHERE match_id='integrity' AND score IS NOT NULL")!=0)throw new AssertionError("Interruption partial commit");
            db.interruptMatch("integrity",20,"Server dừng",Map.of(a.username,2,b.username,0),Set.of(b.username));
            if(GameDatabaseTest.number(c,"SELECT coins FROM users WHERE username='integrity_a'")!=77)throw new AssertionError("Interrupted rewarded");
            if(GameDatabaseTest.number(c,"SELECT forfeited FROM match_results WHERE match_id='integrity' AND username='integrity_b'")!=1)throw new AssertionError("Departed participant lost");
            try {db.completeMatch("integrity","late",false,21,List.of());throw new AssertionError("Interrupted became completed");}catch(IllegalStateException expected) { }
            db.beginMatch("crashed","ROOM",4,"FRUIT",30,List.of(a.username,b.username));
            db.recoverInterruptedMatches();db.recoverInterruptedMatches();
            if(GameDatabaseTest.number(c,"SELECT COUNT(*) FROM matches WHERE status='INTERRUPTED' AND match_id IN ('integrity','crashed')")!=2)throw new AssertionError("Crash recovery");
            if(GameDatabaseTest.number(c,"SELECT games FROM users WHERE username='integrity_a'")!=0)throw new AssertionError("Interrupted counts as completed");
            boolean index=false;
            try(var r=c.getMetaData().getIndexInfo(c.getCatalog(),null,"match_results",false,false)) {
                while(r.next()) if("idx_results_user_time".equals(r.getString("INDEX_NAME")))index=true;
            }
            if(!index)throw new AssertionError("Missing history index");
        }
        System.out.println("DB_INTEGRITY_OK checks PK FK retiredItems staleRAM historyFallback interruptionRollback crashRecovery");
    }
}
