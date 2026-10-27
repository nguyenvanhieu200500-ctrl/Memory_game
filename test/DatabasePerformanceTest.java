package memorygame.server;

import java.sql.*;
import java.util.*;

/** Synthetic isolated data only. Measures current application methods, plus actual SQL plans. */
public final class DatabasePerformanceTest {
    static final String CHAT="(SELECT id,sender,message_text,sent_at FROM direct_messages FORCE INDEX (idx_dm_conversation) WHERE sender='bench00000' AND recipient='bench00001' AND id<9223372036854775807 ORDER BY id DESC LIMIT 51) UNION ALL (SELECT id,sender,message_text,sent_at FROM direct_messages FORCE INDEX (idx_dm_conversation) WHERE sender='bench00001' AND recipient='bench00000' AND id<9223372036854775807 ORDER BY id DESC LIMIT 51) ORDER BY id DESC LIMIT 51";
    static final String HISTORY="SELECT r.result_text FROM match_results r JOIN matches m ON m.match_id=r.match_id WHERE r.username='bench00000' AND m.status IN ('COMPLETED','INTERRUPTED','LEGACY') AND r.result_text IS NOT NULL ORDER BY r.recorded_at DESC,r.legacy_order,r.match_id DESC LIMIT 50";
    static final String RANK="SELECT username,rating,wins,games FROM users ORDER BY rating DESC,wins DESC,CASE WHEN games=0 THEN 0 ELSE wins*100.0/games END DESC";
    interface Work { void run() throws Exception; }
    static void measure(String label,Work work) throws Exception {
        for(int i=0;i<3;i++)work.run();double[] ms=new double[15];
        for(int i=0;i<ms.length;i++){long start=System.nanoTime();work.run();ms[i]=(System.nanoTime()-start)/1e6;}
        Arrays.sort(ms);System.out.printf(Locale.ROOT,"%s median=%.3fms p95=%.3fms n=15%n",label,ms[7],ms[14]);
    }
    public static void main(String[] args) throws Exception {
        // Fail closed BEFORE DatabaseStore can initialize or mutate anything.
        try(Connection guard=DriverManager.getConnection(System.getenv("MEMORY_GAME_DB_URL"),System.getenv("MEMORY_GAME_DB_USER"),System.getenv("MEMORY_GAME_DB_PASSWORD"))) {
            if(!guard.getCatalog().matches("game_test_[a-f0-9]{32}"))throw new SecurityException("Benchmark requires a disposable test schema");
        }
        DatabaseStore db=new DatabaseStore();
        try(Connection c=db.open();Statement s=c.createStatement()) {
            System.out.println("ENV java="+System.getProperty("java.version")+" os="+System.getProperty("os.name")+" arch="+System.getProperty("os.arch")+" cpus="+Runtime.getRuntime().availableProcessors());
            try(ResultSet r=s.executeQuery("SELECT VERSION(),@@innodb_buffer_pool_size")){r.next();System.out.println("MYSQL "+r.getString(1)+" buffer_pool="+r.getLong(2));}
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO users(username,password_hash,rating) VALUES (?, 'synthetic-not-login',?)")) {
                for(int i=0;i<10_000;i++){p.setString(1,String.format("bench%05d",i));p.setInt(2,i%2000);p.addBatch();if(i%1000==999)p.executeBatch();}
            }
            try(PreparedStatement m=c.prepareStatement("INSERT INTO matches(match_id,status,ended_at,reason) VALUES (?,'LEGACY',?,'synthetic')");PreparedStatement r=c.prepareStatement("INSERT INTO match_results(match_id,username,recorded_at,result_text) VALUES (?,?,?,'synthetic result')")) {
                for(int i=0;i<50_000;i++) {
                    String id="bench-match-"+String.format("%06d",i);m.setString(1,id);m.setLong(2,i);m.addBatch();
                    if(i%1000==999)m.executeBatch();
                }
                for(int i=0;i<100_000;i++){r.setString(1,"bench-match-"+String.format("%06d",i/2));r.setString(2,i%2==0?"bench00000":"bench00001");r.setLong(3,i/2);r.addBatch();if(i%1000==999)r.executeBatch();}
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO direct_messages(sender,recipient,message_text,sent_at) VALUES (?,?,'synthetic chat',?)")) {
                for(int i=0;i<200_000;i++){p.setString(1,i<100_000?"bench00000":"bench00002");p.setString(2,i<100_000?"bench00001":"bench00003");p.setLong(3,i);p.addBatch();if(i%1000==999)p.executeBatch();}
            }
            c.commit();c.setAutoCommit(true);s.execute("ANALYZE TABLE users,direct_messages,match_results,matches");
            System.out.println("DATA users=10000 messages=200000 matches=50000 results=100000; hot conversation=100000, hot user history=50000");
            WhisperStore chat=new WhisperStore(db,java.nio.file.Path.of("/tmp/nonexistent-memory-performance-source"));
            measure("chat_page_application",()->chat.page("bench00000","bench00001",0));
            measure("match_history_application",()->db.history("bench00000"));
            var cached=db.loadUsers();
            measure("ranking_current_RAM_10000",()->{var rows=new ArrayList<>(cached.values());rows.sort(Comparator.comparingInt((GameServer.UserRecord u)->u.rating).reversed().thenComparing(Comparator.comparingInt((GameServer.UserRecord u)->u.wins).reversed()).thenComparing(Comparator.comparingDouble(GameServer.UserRecord::winRate).reversed()));});
            measure("ranking_SQL_diagnostic_10000",()->{try(ResultSet r=s.executeQuery(RANK)){while(r.next())r.getString(1);}});
            for(String query:List.of(CHAT,HISTORY,RANK)) {
                System.out.println("EXPLAIN "+query);
                try(ResultSet r=s.executeQuery("EXPLAIN ANALYZE "+query)){while(r.next())System.out.println(r.getString(1));}
            }
        }
    }
}
