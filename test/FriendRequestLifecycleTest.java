package memorygame.test;

import memorygame.common.Protocol.FriendInvitation;
import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;
import memorygame.server.GameServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** File-mode lifecycle test for non-expiring friend invitations and authoritative replies. */
public final class FriendRequestLifecycleTest {
    public static void main(String[] args) throws Exception {
        Path data=Files.createTempDirectory("friend-request-lifecycle-");
        int port; try(ServerSocket reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
        GameServer server=start(port,data); String requestId;
        try(Peer alice=new Peer(port);Peer bob=new Peer(port);Peer carol=new Peer(port)) {
            alice.registerAndLogin("lifeAlice");bob.registerAndLogin("lifeBob");carol.registerAndLogin("lifeCarol");
            alice.until(Type.FRIEND_UPDATE);bob.until(Type.FRIEND_UPDATE);carol.until(Type.FRIEND_UPDATE);
            alice.send(Message.of(Type.FRIEND_SEND).with("username","lifeBob"));
            Message incoming=bob.until(Type.FRIEND_RECEIVED);check(incoming.longValue("sentAt")>0,"missing sent timestamp");
            check(incoming.get("avatar")!=null,"missing avatar payload");alice.until(Type.FRIEND_UPDATE);
            bob.send(Message.of(Type.FRIEND_REPLY).with("requestId",incoming.string("requestId")).with("accepted",true));
            Message accepted=bob.until(Type.FRIEND_UPDATE);
            check(accepted.bool("friendSync")&&accepted.<List<String>>value("friends").contains("lifeAlice"),"accepted state not committed");
            bob.send(Message.of(Type.FRIEND_REPLY).with("requestId",incoming.string("requestId")).with("accepted",true));
            check(bob.until(Type.FRIEND_UPDATE).bool("friendSync"),"repeated accept not idempotent");
            bob.send(Message.of(Type.FRIEND_REPLY).with("requestId",incoming.string("requestId")).with("accepted",false));bob.until(Type.ERROR);
            carol.send(Message.of(Type.FRIEND_SEND).with("username","lifeBob"));
            Message pending=bob.until(Type.FRIEND_RECEIVED);requestId=pending.string("requestId");carol.until(Type.FRIEND_UPDATE);
        }
        server.close();
        Path requests=data.resolve("friend-requests.properties");Properties values=new Properties();
        try(InputStream in=Files.newInputStream(requests)){values.load(in);}
        long old=System.currentTimeMillis()-180_000;values.setProperty(requestId,"lifeCarol|lifeBob|PENDING|"+old);
        String expiredId=UUID.randomUUID().toString();values.setProperty(expiredId,"lifeAlice|lifeCarol|EXPIRED|"+old);
        try(OutputStream out=Files.newOutputStream(requests)){values.store(out,"test old pending and already expired rows");}
        server=start(port,data);
        try(Peer bob=new Peer(port);Peer carol=new Peer(port);Peer alice=new Peer(port)) {
            bob.login("lifeBob");Message sync=bob.until(Type.FRIEND_UPDATE);
            List<FriendInvitation> pending=sync.value("pendingInvitations");
            check(pending.stream().anyMatch(i->i.requestId().equals(requestId)&&i.sentAt()==old),"old PENDING did not survive restart");
            check(pending.stream().noneMatch(i->i.requestId().equals(expiredId)),"EXPIRED invitation revived");
            carol.login("lifeCarol");carol.until(Type.FRIEND_UPDATE);alice.login("lifeAlice");alice.until(Type.FRIEND_UPDATE);
            carol.send(Message.of(Type.FRIEND_SEND).with("username","lifeBob"));carol.until(Type.ERROR);
            alice.send(Message.of(Type.FRIEND_REPLY).with("requestId",requestId).with("accepted",true));alice.until(Type.ERROR);

            bob.send(Message.of(Type.FRIEND_REPLY).with("requestId",requestId).with("accepted",true));bob.close();
            long deadline=System.nanoTime()+5_000_000_000L;
            while(server.monitorSnapshot().online()!=2&&System.nanoTime()<deadline)Thread.yield();
            check(server.monitorSnapshot().online()==2,"disconnected responder remained online");
            try(Peer recovered=new Peer(port)) {
                recovered.login("lifeBob");Message authoritative=recovered.until(Type.FRIEND_UPDATE);
                List<FriendInvitation> afterDrop=authoritative.value("pendingInvitations");
                if(afterDrop.stream().anyMatch(i->i.requestId().equals(requestId))) {
                    recovered.send(Message.of(Type.FRIEND_REPLY).with("requestId",requestId).with("accepted",true));
                    authoritative=recovered.until(Type.FRIEND_UPDATE);
                }
                check(authoritative.<List<String>>value("friends").contains("lifeCarol"),"re-login could not determine reply state");
                recovered.send(Message.of(Type.FRIEND_REPLY).with("requestId",requestId).with("accepted",true));
                check(recovered.until(Type.FRIEND_UPDATE).bool("friendSync"),"repeated accepted response not safe");
            }
            alice.send(Message.of(Type.FRIEND_SEND).with("username","lifeCarol"));
            Message declineRequest=carol.until(Type.FRIEND_RECEIVED);alice.until(Type.FRIEND_UPDATE);
            carol.send(Message.of(Type.FRIEND_REPLY).with("requestId",declineRequest.string("requestId")).with("accepted",false));
            Message declined=carol.until(Type.FRIEND_UPDATE);
            check(declined.bool("friendSync")&&declined.<List<FriendInvitation>>value("pendingInvitations").stream().noneMatch(i->i.requestId().equals(declineRequest.string("requestId"))),"decline not synchronized");
        } finally {server.close();}
        System.out.println("FRIEND_REQUEST_LIFECYCLE_OK olderThanTwoMinutes offlineRestart expiredStayedClosed duplicateSender recipientCheck accept decline idempotent lostReplyResync");
    }
    private static GameServer start(int port,Path data)throws Exception {
        GameServer server=new GameServer(port,data);
        Thread thread=new Thread(()->{try{server.start();}catch(IOException e){throw new UncheckedIOException(e);}});thread.setDaemon(true);thread.start();
        for(int i=0;i<100;i++){try(Socket ignored=new Socket("127.0.0.1",port)){return server;}catch(IOException e){Thread.sleep(20);}}
        throw new AssertionError("server not ready");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final class Peer implements AutoCloseable {
        final Socket socket;final ObjectOutputStream out;final ObjectInputStream in;
        Peer(int port)throws Exception{socket=new Socket("127.0.0.1",port);socket.setSoTimeout(4000);out=new ObjectOutputStream(socket.getOutputStream());out.flush();in=new ObjectInputStream(socket.getInputStream());}
        void send(Message m)throws Exception{out.reset();out.writeObject(m);out.flush();}
        void registerAndLogin(String name)throws Exception{send(Message.of(Type.REGISTER).with("username",name).with("password","secret123"));until(Type.REGISTER_OK);login(name);}
        void login(String name)throws Exception{send(Message.of(Type.LOGIN).with("username",name).with("password","secret123"));until(Type.LOGIN_OK);}
        Message until(Type type)throws Exception{for(int i=0;i<100;i++){Message m=(Message)in.readObject();if(m.type()==type)return m;if(m.type()==Type.ERROR)throw new AssertionError("Unexpected "+m);}throw new AssertionError("missing "+type);}
        public void close()throws Exception{socket.close();}
    }
}
