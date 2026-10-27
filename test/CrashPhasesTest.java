package memorygame.test;

import memorygame.common.Protocol.*;
import memorygame.server.GameServer;
import java.io.*;
import java.nio.file.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Kills a separate JVM in four distinct durable phases, then verifies the exact saved state. */
public final class CrashPhasesTest {
    static Object snapshot(String id)throws Exception {
        try(var c=GamePersistenceProtocolTest.connection();var p=c.prepareStatement("SELECT payload FROM match_snapshots WHERE match_id=?")) {
            p.setString(1,id);try(var r=p.executeQuery()){if(!r.next())throw new AssertionError("No snapshot");try(var in=new ObjectInputStream(new ByteArrayInputStream(r.getBytes(1)))){return in.readObject();}}
        }
    }
    public static void main(String[] args)throws Exception {
        for(String phase:List.of("FIRST","WRONG_PAIR","TURN","SETTLING"))run(phase);
        System.out.println("CRASH_PHASES_OK hardKill firstCard wrongPair turnChange settling exactBoard scores activePlayers timers hiddenValues onceOnlyRewards");
    }
    static void run(String phase)throws Exception {
        Path dir=Files.createTempDirectory("crash-phase-");
        Process child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("java.class.path"),GameCrashRecoveryTest.class.getName(),dir.toString()).inheritIO().start();
        String aName="a_"+phase.toLowerCase(),bName="b_"+phase.toLowerCase(),id;Object stored;
        try {
            boolean ready=false;for(int i=0;i<160;i++){try(var socket=new Socket("127.0.0.1",ProfileTest.PORT)){ready=true;break;}catch(Exception e){Thread.sleep(50);}}
            GameRecoveryTest.check(ready,"Child not ready");
            try(var a=new ProfileTest.Client();var b=new ProfileTest.Client()) {
                ServerInterruptionTest.login(a,aName);ServerInterruptionTest.login(b,bName);
                a.send(Message.of(Type.ROOM_CREATE).with("boardSize",4).with("capacity",2).with("cardTheme","FRUIT"));
                b.send(Message.of(Type.ROOM_JOIN).with("code",a.await(Type.ROOM_UPDATE).string("code")));ServerInterruptionTest.room(a,2,false);
                a.send(Message.of(Type.ROOM_READY).with("ready",true));b.send(Message.of(Type.ROOM_READY).with("ready",true));ServerInterruptionTest.room(a,2,true);
                a.send(Message.of(Type.ROOM_START));Message start=a.await(Type.GAME_START);b.await(Type.GAME_START);id=start.string("matchId");
                Object initialRoom=GameRecoveryTest.field(snapshot(id),"room"),initialGame=GameRecoveryTest.field(initialRoom,"game");
                int[] values=(int[])GameRecoveryTest.field(initialGame,"values");
                var actor=start.string("current").equals(aName)?a:b;
                actor.send(Message.of(Type.CARD_PICK).with("index",0));a.await(Type.CARD_REVEALED);
                if(phase.equals("WRONG_PAIR")||phase.equals("TURN")) {
                    int wrong=1;while(values[wrong]==values[0])wrong++;
                    actor.send(Message.of(Type.CARD_PICK).with("index",wrong));a.await(Type.MATCH_RESULT);
                    if(phase.equals("TURN")){a.await(Type.CARD_HIDDEN);a.await(Type.TURN_UPDATE);}
                }else if(phase.equals("SETTLING")) {
                    try(var c=GamePersistenceProtocolTest.connection();var sql=c.createStatement()){sql.executeUpdate("CREATE TRIGGER crash_settle BEFORE UPDATE ON match_results FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='hold settlement for crash'");}
                    b.send(Message.of(Type.ROOM_LEAVE));a.await(Type.ERROR);
                }
                child.destroyForcibly();GameRecoveryTest.check(child.waitFor(5,TimeUnit.SECONDS),"Kill failed");
                stored=snapshot(id);
            }
        }finally{if(child.isAlive()){child.destroyForcibly();child.waitFor();}}
        if(phase.equals("SETTLING"))try(var c=GamePersistenceProtocolTest.connection();var sql=c.createStatement()){sql.executeUpdate("DROP TRIGGER crash_settle");}
        GameServer server=new GameServer(ProfileTest.PORT,dir);Thread thread=ProfileTest.start(server);
        try {
            if(!phase.equals("SETTLING"))try(var a=new ProfileTest.Client();var b=new ProfileTest.Client()) {
                GameRecoveryTest.login(a,aName);GameRecoveryTest.login(b,bName);Message state=a.await(Type.GAME_START);b.await(Type.GAME_START);
                Object expected=GameRecoveryTest.field(GameRecoveryTest.field(stored,"room"),"game");
                Object room=((Map<?,?>)GameRecoveryTest.field(server,"rooms")).values().iterator().next(),actual=GameRecoveryTest.field(room,"game");
                for(String field:List.of("values","states","matchedOwners"))GameRecoveryTest.check(Objects.deepEquals(GameRecoveryTest.field(expected,field),GameRecoveryTest.field(actual,field)),"Lost "+field+" "+phase);
                for(String field:List.of("players","active","scores","selected","phase","currentIndex"))GameRecoveryTest.check(Objects.equals(GameRecoveryTest.field(expected,field),GameRecoveryTest.field(actual,field)),"Lost "+field+" "+phase);
                GameRecoveryTest.check(GameRecoveryTest.field(stored,"turnRemaining").equals(GameRecoveryTest.field(actual,"remainingTurn")),"Turn time lost");
                GameRecoveryTest.check(GameRecoveryTest.field(stored,"actionRemaining").equals(GameRecoveryTest.field(actual,"remainingAction")),"Action time lost");
                int[] states=state.value("states"),visible=state.value("values");for(int i=0;i<states.length;i++)if(states[i]==0)GameRecoveryTest.check(visible[i]==-1,"Hidden value leaked");
                a.send(Message.of(Type.GAME_RESUME).with("matchId",id));a.await(Type.GAME_PAUSED);
                b.send(Message.of(Type.GAME_RESUME).with("matchId",id));a.await(Type.GAME_START);b.await(Type.GAME_START);
                b.send(Message.of(Type.ROOM_LEAVE));a.await(Type.GAME_END);
            }
            try(var c=GamePersistenceProtocolTest.connection();var p=c.prepareStatement("SELECT (SELECT COUNT(*) FROM match_snapshots WHERE match_id=?),(SELECT COUNT(*) FROM coin_transactions WHERE match_id=?),(SELECT status FROM matches WHERE match_id=?)")) {
                p.setString(1,id);p.setString(2,id);p.setString(3,id);try(var r=p.executeQuery()){r.next();GameRecoveryTest.check(r.getInt(1)==0&&r.getInt(2)==2&&r.getString(3).equals("COMPLETED"),"Settlement not atomic "+phase);}
            }
        }finally{server.close();thread.join(2000);}
        System.out.println("CRASH_PHASE_OK "+phase);
    }
}
