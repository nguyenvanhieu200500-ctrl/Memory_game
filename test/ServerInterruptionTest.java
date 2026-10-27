package memorygame.test;

import memorygame.common.Protocol.*;
import memorygame.server.GameServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class ServerInterruptionTest {
    static void login(ProfileTest.Client c,String name) throws Exception {
        c.send(Message.of(Type.REGISTER).with("username",name).with("password","secret123"));c.await(Type.REGISTER_OK);
        c.send(Message.of(Type.LOGIN).with("username",name).with("password","secret123"));c.await(Type.LOGIN_OK);
    }
    static void room(ProfileTest.Client c,int size,boolean ready) throws Exception {
        for(int i=0;i<30;i++) {
            Message m=c.await(Type.ROOM_UPDATE);List<Map<String,Object>> players=m.value("players");
            if(players!=null && players.size()==size && (!ready || players.stream().allMatch(p->Boolean.TRUE.equals(p.get("ready")))))return;
        }
        throw new AssertionError("Room update missing");
    }
    static long number(Connection c,String sql) throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("shutdown-test");
        GameServer server=new GameServer(ProfileTest.PORT,dir);Thread thread=ProfileTest.start(server);
        String id;
        try(var a=new ProfileTest.Client();var b=new ProfileTest.Client();var d=new ProfileTest.Client()) {
            login(a,"stop_a");login(b,"stop_b");login(d,"stop_c");
            a.send(Message.of(Type.ROOM_CREATE).with("boardSize",4).with("capacity",3).with("cardTheme","FRUIT"));
            String code=a.await(Type.ROOM_UPDATE).string("code");
            b.send(Message.of(Type.ROOM_JOIN).with("code",code));b.await(Type.ROOM_UPDATE);
            d.send(Message.of(Type.ROOM_JOIN).with("code",code));d.await(Type.ROOM_UPDATE);room(a,3,false);
            a.send(Message.of(Type.ROOM_READY).with("ready",true));b.send(Message.of(Type.ROOM_READY).with("ready",true));d.send(Message.of(Type.ROOM_READY).with("ready",true));
            room(a,3,true);a.send(Message.of(Type.ROOM_START));id=a.await(Type.GAME_START).string("matchId");
            d.send(Message.of(Type.ROOM_LEAVE));room(a,2,false);
            server.close();server.close();thread.join(2000);
        } finally {server.close();thread.join(2000);}
        try(var c=GamePersistenceProtocolTest.connection();var s=c.createStatement()) {
            if(number(c,"SELECT COUNT(*) FROM matches WHERE status='PLAYING'")!=1)throw new AssertionError("Shutdown must preserve game");
            if(number(c,"SELECT COUNT(*) FROM match_snapshots")!=1)throw new AssertionError("Shutdown snapshot missing");
            if(number(c,"SELECT COUNT(*) FROM match_results WHERE match_id='"+id+"'")!=3)throw new AssertionError("Lost departed participant");
            if(number(c,"SELECT SUM(games+coins) FROM users")!=0)throw new AssertionError("Shutdown awarded");
            s.executeUpdate("INSERT INTO matches(match_id,room_code,board_size,card_theme,player_count,started_at,status) VALUES('abrupt','ROOM',4,'FRUIT',2,1,'PLAYING')");
            s.executeUpdate("INSERT INTO match_results(match_id,username,recorded_at) VALUES('abrupt','stop_a',1),('abrupt','stop_b',1)");
        }
        server=new GameServer(ProfileTest.PORT,dir);thread=ProfileTest.start(server);
        try(var c=GamePersistenceProtocolTest.connection()) {
            if(number(c,"SELECT COUNT(*) FROM matches WHERE status='INTERRUPTED'")!=1)throw new AssertionError("Missing snapshot must be interrupted");
            try(var a=new ProfileTest.Client()) {
                a.send(Message.of(Type.LOGIN).with("username","stop_a").with("password","secret123"));a.await(Type.LOGIN_OK);
                Message state=a.await(Type.GAME_START);
                List<String> required=state.value("required");
                if(!state.bool("paused") || required.contains("stop_c") || required.size()!=2)throw new AssertionError("Departed participant restored as active");
            }
            if(number(c,"SELECT SUM(games+coins) FROM users")!=0)throw new AssertionError("Recovery awarded");
        } finally {server.close();thread.join(2000);}
        System.out.println("SERVER_INTERRUPTION_OK gracefulClose departedParticipant repeatedClose crashRecovery noRewards");
    }
}
