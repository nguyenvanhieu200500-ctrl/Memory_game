package memorygame.server;

import java.util.*;

/** Simulate the previous release with the three new tables but without CHECK/time index upgrades. */
public final class PreviousGameSchemaTest {
    public static void main(String[] args) throws Exception {
        DatabaseStore db=new DatabaseStore();
        var user=new GameServer.UserRecord("previous","hash");user.coins=123;db.saveUsers(Map.of(user.username,user));
        try(var c=db.open();var s=c.createStatement()) {
            for(String[] check:GameSchema.CHECKS)s.executeUpdate("ALTER TABLE "+check[0]+" DROP CHECK "+check[1]);
            s.executeUpdate("ALTER TABLE match_results DROP INDEX idx_results_user_time, DROP COLUMN recorded_at");
            s.executeUpdate("INSERT INTO matches(match_id,board_size,card_theme,player_count,started_at,ended_at,status,reason,abnormal) VALUES('previous_match',4,'',2,1,2,'COMPLETED','old result',FALSE)");
            s.executeUpdate("INSERT INTO match_results(match_id,username,score,placement,winner,forfeited,coin_reward,rating_delta,result_text) VALUES('previous_match','previous',8,1,TRUE,FALSE,50,3,'old structured history')");
        }
        db=new DatabaseStore();
        try(var c=db.open();var s=c.createStatement();var r=s.executeQuery("SELECT m.card_theme,r.recorded_at,r.score,u.coins FROM matches m JOIN match_results r ON m.match_id=r.match_id JOIN users u ON r.username=u.username WHERE m.match_id='previous_match'")) {
            r.next();if(!"UNKNOWN".equals(r.getString(1))||r.getLong(2)!=2||r.getInt(3)!=8||r.getInt(4)!=123)throw new AssertionError("Previous schema data lost");
        }
        DatabaseIntegrityChecks.run(db);
        System.out.println("PREVIOUS_GAME_SCHEMA_OK additiveUpgrade preservedResults backfilledTime");
    }
}
