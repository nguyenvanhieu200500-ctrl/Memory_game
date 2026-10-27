package memorygame.test;

import memorygame.common.Protocol.*;
import memorygame.server.GameServer;
import java.nio.file.*;
import java.util.*;

/** Real restart and the full production 120-second reconnect deadline. */
public final class RecoveryWaitTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("recovery-wait-");
        GameServer server=new GameServer(ProfileTest.PORT,dir);Thread thread=ProfileTest.start(server);String id;
        try(var a=new ProfileTest.Client();var b=new ProfileTest.Client()) {
            ServerInterruptionTest.login(a,"wait_a");ServerInterruptionTest.login(b,"wait_b");
            a.send(Message.of(Type.ROOM_CREATE).with("boardSize",4).with("capacity",2).with("cardTheme","FRUIT"));
            b.send(Message.of(Type.ROOM_JOIN).with("code",a.await(Type.ROOM_UPDATE).string("code")));
            ServerInterruptionTest.room(a,2,false);
            a.send(Message.of(Type.ROOM_READY).with("ready",true));b.send(Message.of(Type.ROOM_READY).with("ready",true));
            ServerInterruptionTest.room(a,2,true);a.send(Message.of(Type.ROOM_START));
            id=a.await(Type.GAME_START).string("matchId");b.await(Type.GAME_START);
        }finally {server.close();thread.join(2000);}
        server=new GameServer(ProfileTest.PORT,dir);thread=ProfileTest.start(server);
        try(var a=new ProfileTest.Client()) {
            GameRecoveryTest.login(a,"wait_a");Message state=a.await(Type.GAME_START);
            long deadline=state.longValue("recoveryDeadline");
            GameRecoveryTest.check(deadline>System.currentTimeMillis()+110_000 && deadline<=System.currentTimeMillis()+120_000,"120-second deadline");
            a.send(Message.of(Type.GAME_RESUME).with("matchId",id));a.await(Type.GAME_PAUSED);
            // Do not reset the waiting window on another server restart.
            server.close();thread.join(2000);
            server=new GameServer(ProfileTest.PORT,dir);thread=ProfileTest.start(server);
            try(var fresh=new ProfileTest.Client()) {
                GameRecoveryTest.login(fresh,"wait_a");
                GameRecoveryTest.check(fresh.await(Type.GAME_START).longValue("recoveryDeadline")==deadline,"Restart extended deadline");
                fresh.send(Message.of(Type.GAME_RESUME).with("matchId",id));fresh.await(Type.GAME_PAUSED);
                while(System.currentTimeMillis()<deadline+4000)Thread.sleep(1000);
                Message end=fresh.await(Type.GAME_END);
                List<Map<String,Object>> results=end.value("results");
                var missing=results.stream().filter(r->r.get("username").equals("wait_b")).findFirst().orElseThrow();
                GameRecoveryTest.check(Boolean.TRUE.equals(missing.get("forfeited")),"Absent player not forfeited");
                try(var c=GamePersistenceProtocolTest.connection()) {
                    GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT COUNT(*) FROM match_snapshots")==0,"Finished snapshot retained");
                    GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT COUNT(*) FROM coin_transactions WHERE reason='MATCH_REWARD'")==2,"Reward count");
                }
            }
        }finally {server.close();thread.join(2000);}
        System.out.println("RECOVERY_WAIT_OK real120Seconds deadlineSurvivesRestart missingPlayerForfeit atomicSettlement");
    }
}
