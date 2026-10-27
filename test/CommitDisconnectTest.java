package memorygame.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static memorygame.server.GameDatabaseTest.*;
import memorygame.common.Protocol.*;

/** Real TCP proxy drops COMMIT's OK packet before JDBC receives it. No production hooks. */
public final class CommitDisconnectTest {
    static final class Proxy implements AutoCloseable {
        final ServerSocket listener=new ServerSocket(0,50,InetAddress.getLoopbackAddress());
        final ExecutorService pool=Executors.newCachedThreadPool();
        final AtomicBoolean armed=new AtomicBoolean();
        final AtomicInteger dropped=new AtomicInteger(),rejectFresh=new AtomicInteger();
        final AtomicBoolean blockAfterDrop=new AtomicBoolean();
        final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
        Proxy() throws IOException {
            pool.submit(()->{while(!listener.isClosed())try {
                Socket client=listener.accept();
                if(rejectFresh.getAndUpdate(n->Math.max(0,n-1))>0){client.close();continue;}
                Socket server=new Socket("127.0.0.1",3307);
                sockets.add(client);sockets.add(server);AtomicBoolean commit=new AtomicBoolean();
                pool.submit(()->relay(client,server,commit,true));pool.submit(()->relay(server,client,commit,false));
            }catch(IOException ignored){}});
        }
        void relay(Socket from,Socket to,AtomicBoolean commit,boolean upstream) {
            try {
                InputStream in=from.getInputStream();OutputStream out=to.getOutputStream();
                while(true) {
                    byte[] header=in.readNBytes(4);if(header.length!=4)break;
                    int length=(header[0]&255)|((header[1]&255)<<8)|((header[2]&255)<<16);
                    byte[] payload=in.readNBytes(length);if(payload.length!=length)break;
                    if(upstream && length>1 && payload[0]==3 && new String(payload,1,length-1,StandardCharsets.UTF_8).trim().equalsIgnoreCase("commit") && armed.compareAndSet(true,false))commit.set(true);
                    if(!upstream && commit.get() && length>0 && payload[0]==0) {dropped.incrementAndGet();if(blockAfterDrop.getAndSet(false))rejectFresh.set(1);break;}
                    out.write(header);out.write(payload);out.flush();
                }
            }catch(IOException ignored){}finally {try{from.close();to.close();}catch(IOException ignored){}}
        }
        public void close() throws IOException {listener.close();for(Socket s:sockets)s.close();pool.shutdownNow();}
    }
    public static void main(String[] args) throws Exception {
        if(args.length==0) {
            try(Proxy proxy=new Proxy()) {
                ProcessBuilder child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("java.class.path"),CommitDisconnectTest.class.getName(),"child");
                child.environment().put("MEMORY_GAME_DB_URL",System.getenv("MEMORY_GAME_DB_URL").replace(":3307/",":"+proxy.listener.getLocalPort()+"/"));
                // Control channel arms a single cut only after schema/setup transactions finish.
                child.redirectError(ProcessBuilder.Redirect.INHERIT);
                Process process=child.start();
                try(var input=new BufferedReader(new InputStreamReader(process.getInputStream()));var output=new PrintWriter(process.getOutputStream(),true)) {
                    String line;while((line=input.readLine())!=null) {
                        if(line.equals("REJECT")){proxy.rejectFresh.set(1);output.println("READY");}
                        else if(line.equals("ARM")||line.equals("ARM_BLOCK")){proxy.blockAfterDrop.set(line.equals("ARM_BLOCK"));proxy.armed.set(true);output.println("READY");}else System.out.println(line);
                    }
                }
                check(process.waitFor()==0,"Proxy child failed");check(proxy.dropped.get()==6,"Must drop six real COMMIT OK packets");
                System.out.println("COMMIT_DISCONNECT_OK tcpDroppedCommitACK=6 purchase start snapshot settlement retry noDoubleDebit noDoubleReward pendingStartReconciliation unavailableBeforePurchase unknownPurchaseCommit");
            }
            return;
        }
        DatabaseStore db=new DatabaseStore();
        var a=new GameServer.UserRecord("commit_a",PasswordHashes.hash("secret123"));a.coins=500;
        var b=new GameServer.UserRecord("commit_b",PasswordHashes.hash("secret123"));db.saveUsers(Map.of("commit_a",a,"commit_b",b));
        BufferedReader control=new BufferedReader(new InputStreamReader(System.in));
        arm(control);check(db.purchase("commit_a","theme_ocean").isEmpty(),"Purchase ACK reconciliation");
        db.purchase("commit_a","theme_ocean");
        arm(control);db.beginMatch("commit-match","ROOM",4,"FRUIT",1,List.of("commit_a","commit_b"),new byte[]{1});
        arm(control);check(db.saveSnapshot("commit-match",new byte[]{2},1)==2,"Snapshot ACK reconciliation");
        var results=List.of(result("commit_a",8,1,true,false,3,50,true),result("commit_b",0,2,false,false,-1,10,false));
        arm(control);db.completeMatch("commit-match","done",false,2,results);
        db.completeMatch("commit-match","retry",false,2,results);
        try(Connection c=db.open()) {
            check(number(c,"SELECT COUNT(*) FROM coin_transactions")==3,"Duplicate journal");
            check(number(c,"SELECT COUNT(*) FROM user_items")==1,"Duplicate item");
            check(number(c,"SELECT COUNT(*) FROM match_snapshots")==0,"Snapshot retained");
            check(number(c,"SELECT SUM(games) FROM users")==2,"Double statistics");
            check(number(c,"SELECT MIN(coins) FROM users")>=0,"Negative coins");
            check(number(c,"SELECT coins FROM users WHERE username='commit_a'")==550-number(c,"SELECT price FROM products WHERE product_id='theme_ocean'"),"Double debit/reward");
        }
        System.out.println("REJECT");System.out.flush();check("READY".equals(control.readLine()),"No proxy control");
        try { db.purchase("commit_a","theme_gold");throw new AssertionError("Unavailable DB reported purchase success"); }
        catch(IllegalStateException expected) { }
        try(Connection c=db.open()) {
            check(number(c,"SELECT COUNT(*) FROM user_items WHERE product_id='theme_gold'")==0,"Failed connection granted item");
            check(number(c,"SELECT COUNT(*) FROM coin_transactions")==3,"Failed connection changed journal");
        }
        System.out.println("ARM_BLOCK");System.out.flush();check("READY".equals(control.readLine()),"No proxy control");
        try { db.purchase("commit_a","theme_gold");throw new AssertionError("Unknown commit reported success"); }
        catch(IllegalStateException expected) { check(DatabaseStore.unknownCommit(expected),"Must identify unknown commit"); }
        check(!db.purchase("commit_a","theme_gold").isEmpty(),"Retry must recognize already committed purchase");
        try(Connection c=db.open()) {
            check(number(c,"SELECT COUNT(*) FROM coin_transactions")==4,"Unknown commit retry double debit");
            check(number(c,"SELECT COUNT(*) FROM user_items WHERE product_id='theme_gold'")==1,"Unknown commit retry duplicate item");
            check(number(c,"SELECT coins FROM users WHERE username='commit_a'")==550-number(c,"SELECT SUM(price) FROM products WHERE product_id IN ('theme_ocean','theme_gold')"),"Unknown commit changed balance twice");
        }
        int port;try(ServerSocket free=new ServerSocket(0)){port=free.getLocalPort();}
        GameServer server=new GameServer(port,java.nio.file.Files.createTempDirectory("uncertain-start-"));
        Thread thread=new Thread(()->{try{server.start();}catch(Exception e){throw new RuntimeException(e);}});thread.setDaemon(true);thread.start();
        for(int i=0;i<100;i++){try(Socket ready=new Socket("127.0.0.1",port)){break;}catch(IOException e){Thread.sleep(30);}}
        try(Peer first=new Peer(port);Peer second=new Peer(port)) {
            first.send(Message.of(Type.LOGIN).with("username","commit_a").with("password","secret123"));first.await(Type.LOGIN_OK);
            second.send(Message.of(Type.LOGIN).with("username","commit_b").with("password","secret123"));second.await(Type.LOGIN_OK);
            first.send(Message.of(Type.ROOM_CREATE).with("boardSize",4).with("capacity",2).with("cardTheme","FRUIT"));
            second.send(Message.of(Type.ROOM_JOIN).with("code",first.await(Type.ROOM_UPDATE).string("code")));
            first.send(Message.of(Type.ROOM_READY).with("ready",true));second.send(Message.of(Type.ROOM_READY).with("ready",true));
            while(true){Message room=first.await(Type.ROOM_UPDATE);List<Map<String,Object>> players=room.value("players");if(players.size()==2&&players.stream().allMatch(r->Boolean.TRUE.equals(r.get("ready"))))break;}
            System.out.println("ARM_BLOCK");System.out.flush();check("READY".equals(control.readLine()),"No proxy control");
            first.send(Message.of(Type.ROOM_START));first.await(Type.ERROR);
            first.send(Message.of(Type.ROOM_START));first.await(Type.ERROR);
            Message restored=first.await(Type.GAME_START);check(restored.bool("paused"),"Uncertain start must await confirmation");
            try(Connection c=db.open()){check(number(c,"SELECT COUNT(*) FROM matches")==2,"Uncertain start created second UUID");}
            first.send(Message.of(Type.GAME_RESUME).with("matchId",restored.string("matchId")));first.await(Type.GAME_PAUSED);
            second.send(Message.of(Type.GAME_RESUME).with("matchId",restored.string("matchId")));first.await(Type.GAME_START);
            second.send(Message.of(Type.ROOM_LEAVE));first.await(Type.GAME_END);
        }finally{server.close();thread.join(2000);}
    }
    static final class Peer implements AutoCloseable {
        final Socket socket;final ObjectInputStream in;final ObjectOutputStream out;
        Peer(int port)throws Exception{socket=new Socket("127.0.0.1",port);socket.setSoTimeout(10000);out=new ObjectOutputStream(socket.getOutputStream());out.flush();in=new ObjectInputStream(socket.getInputStream());}
        void send(Message m)throws Exception{out.reset();out.writeObject(m);out.flush();}
        Message await(Type type)throws Exception{for(int i=0;i<100;i++){Message m=(Message)in.readObject();if(m.type()==type)return m;}throw new AssertionError("Missing "+type);}
        public void close()throws IOException{socket.close();}
    }
    static void arm(BufferedReader control)throws IOException {System.out.println("ARM");System.out.flush();if(!"READY".equals(control.readLine()))throw new IOException("No proxy control");}
}
