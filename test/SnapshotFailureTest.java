package memorygame.test;

import memorygame.common.Protocol.*;
import memorygame.server.GameServer;
import java.nio.file.*;
import java.util.*;

public final class SnapshotFailureTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("snapshot-failure-");
        GameServer server=new GameServer(ProfileTest.PORT,dir);Thread thread=ProfileTest.start(server);
        try(var a=new ProfileTest.Client();var b=new ProfileTest.Client();var c=GamePersistenceProtocolTest.connection();var sql=c.createStatement()) {
            ServerInterruptionTest.login(a,"failure_a");ServerInterruptionTest.login(b,"failure_b");
            a.send(Message.of(Type.ROOM_CREATE).with("boardSize",4).with("capacity",2).with("cardTheme","FRUIT"));
            b.send(Message.of(Type.ROOM_JOIN).with("code",a.await(Type.ROOM_UPDATE).string("code")));
            ServerInterruptionTest.room(a,2,false);
            a.send(Message.of(Type.ROOM_READY).with("ready",true));b.send(Message.of(Type.ROOM_READY).with("ready",true));
            ServerInterruptionTest.room(a,2,true);a.send(Message.of(Type.ROOM_START));
            Message start=a.await(Type.GAME_START);b.await(Type.GAME_START);
            sql.executeUpdate("CREATE TRIGGER fail_snapshot BEFORE UPDATE ON match_snapshots FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='snapshot unavailable'");
            (start.string("current").equals("failure_a")?a:b).send(Message.of(Type.CARD_PICK).with("index",0));
            Message rollback=a.await(Type.GAME_START);
            GameRecoveryTest.check(rollback.bool("paused") && rollback.<int[]>value("states")[0]==0,"Failed write leaked tentative card");
            a.await(Type.ERROR);
            sql.executeUpdate("DROP TRIGGER fail_snapshot");
            // Force settlement failure only after a durable SETTLING snapshot exists.
            sql.executeUpdate("CREATE TRIGGER fail_settlement BEFORE UPDATE ON match_results FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='settlement unavailable'");
            Object lock=GameRecoveryTest.field(server,"lock");
            synchronized(lock) {
                Object room=((Map<?,?>)GameRecoveryTest.field(server,"rooms")).values().iterator().next();
                var finish=GameServer.class.getDeclaredMethod("finishGame",room.getClass(),String.class,boolean.class);finish.setAccessible(true);
                finish.invoke(server,room,"Tất cả người chơi không hoạt động trong 60 giây",true);
            }
            GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT COUNT(*) FROM match_snapshots")==1,"Settlement failure lost snapshot");
            GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT SUM(coins+games) FROM users")==0,"Failed settlement partially rewarded");
            server.close();thread.join(2000);sql.executeUpdate("DROP TRIGGER fail_settlement");
        }finally {server.close();thread.join(2000);}
        server=new GameServer(ProfileTest.PORT,dir);thread=ProfileTest.start(server);
        try(var c=GamePersistenceProtocolTest.connection()) {
            GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT COUNT(*) FROM matches WHERE status='COMPLETED'")==1,"SETTLING did not complete on restart");
            GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT COUNT(*) FROM match_snapshots")==0,"Settled snapshot retained");
            GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT COUNT(*) FROM coin_transactions")==2,"Settlement journal count");
        }finally {server.close();thread.join(2000);}
        System.out.println("SNAPSHOT_FAILURE_OK cardRollback pause settlementRollback restartSettling atomicRewards");
    }
}
