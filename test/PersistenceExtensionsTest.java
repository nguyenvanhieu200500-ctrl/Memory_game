package memorygame.server;

import memorygame.common.Protocol.ChatEntry;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static memorygame.server.GameDatabaseTest.*;

/** Real MySQL, isolated by DatabaseFlowsTest. */
public final class PersistenceExtensionsTest {
    static void fails(Runnable action) {try{action.run();throw new AssertionError("Expected failure");}catch(IllegalStateException expected){}}
    public static void main(String[] args) throws Exception {
        DatabaseStore db=new DatabaseStore();
        Map<String,GameServer.UserRecord> users=new HashMap<>();
        for(String name:List.of("alice","bob","charlie","dana")) {
            var user=new GameServer.UserRecord(name,"hash");user.coins=500;users.put(name,user);
        }
        db.saveUsers(users);
        FriendshipStore friends=new FriendshipStore(db);
        Properties legacy=new Properties();legacy.setProperty("alice:bob","true");
        friends.importLegacy(legacy);friends.importLegacy(legacy);
        check(friends.friends("bob").equals(List.of("alice")),"Legacy friendship missing");
        fails(()->friends.request("alice","alice"));fails(()->friends.request("bob","alice"));
        String request=friends.request("alice","charlie");
        fails(()->friends.request("charlie","alice"));fails(()->friends.reply("bob",request,true));
        check(new FriendshipStore(new DatabaseStore()).pending("charlie").get(0).id().equals(request),"Pending lost on restart");
        friends.reply("charlie",request,true);friends.reply("charlie",request,true);
        String decline=friends.request("bob","dana");friends.reply("dana",decline,false);
        String reverse=friends.request("dana","bob");friends.reply("bob",reverse,true);
        try(Connection c=db.open();Statement s=c.createStatement()) {
            check(number(c,"SELECT COUNT(*) FROM friendships")==3,"Duplicate pairs");
            check(number(c,"SELECT COUNT(*) FROM coin_transactions")==0,"Invented old coin history");
            s.executeUpdate("CREATE TRIGGER fail_friend BEFORE INSERT ON friendships FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='failure'");
            fails(()->friends.request("alice","dana"));
            s.executeUpdate("DROP TRIGGER fail_friend");
            check(friends.pending("dana").isEmpty(),"Failed invitation persisted");
            s.executeUpdate("INSERT INTO private_messages(sender,recipient,message_text,sent_at) VALUES ('alice','bob','real SQL source',100)");
        }
        new DatabaseStore();new DatabaseStore();
        Path source=Files.createTempDirectory("legacy-chat-").resolve("private-messages.bin");
        WhisperStore file=new WhisperStore(null,source);
        file.append("bob",new ChatEntry("alice","real file source",101));
        file.append("bob",new ChatEntry("alice","real file source",101));
        WhisperStore chat=new WhisperStore(db,source);new WhisperStore(db,source);
        for(int i=0;i<105;i++)chat.append(i%2==0?"bob":"alice",new ChatEntry(i%2==0?"alice":"bob","message "+i,1000+i));
        var first=chat.page("alice","bob",0);var second=chat.page("alice","bob",first.before());
        var third=chat.page("alice","bob",second.before());
        check(first.messages().size()==50 && second.messages().size()==50 && third.messages().size()==8 && !third.hasMore(),"Pagination loss/duplicate");
        check(chat.page("charlie","bob",0).messages().isEmpty(),"Third party accessed history");
        chat.markRead("charlie","alice",Long.MAX_VALUE);
        try(Connection c=db.open();Statement sql=c.createStatement()) {
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE read_at IS NOT NULL")==0,"Third party read receipt");
            chat.markRead("bob","alice",first.newest());
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE recipient='bob' AND read_at IS NULL")==0,"Missing read receipts");
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE recipient='alice' AND read_at IS NOT NULL")==0,"Read receipt scope");
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE legacy_key IS NOT NULL")==3,"Legacy chat imported twice/lost duplicates");
            check(Files.exists(source),"Source deleted");
            db.purchase("alice","theme_ocean");db.purchase("alice","theme_ocean");
            check(number(c,"SELECT COUNT(*) FROM coin_transactions WHERE reason='PURCHASE'")==1,"Duplicate purchase journal");
            sql.executeUpdate("CREATE TRIGGER fail_coins BEFORE INSERT ON coin_transactions FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='failure'");
            long balance=number(c,"SELECT coins FROM users WHERE username='alice'");
            fails(()->db.purchase("alice","theme_gold"));
            check(number(c,"SELECT coins FROM users WHERE username='alice'")==balance,"Journal failure debited balance");
            check(number(c,"SELECT COUNT(*) FROM user_items WHERE product_id='theme_gold'")==0,"Journal failure granted item");
            sql.executeUpdate("DROP TRIGGER fail_coins");
            db.beginMatch("snapshot","ROOM",4,"FRUIT",1,List.of("alice","bob"),new byte[]{1});
            check(db.saveSnapshot("snapshot",new byte[]{2},1)==2,"Revision increment");
            fails(()->db.saveSnapshot("snapshot",new byte[]{3},1));
            check(Arrays.equals(db.playingSnapshots().get(0).payload(),new byte[]{2}),"Stale write overwrote snapshot");
            var results=List.of(result("alice",8,1,true,false,3,50,true),result("bob",0,2,false,false,-1,10,false));
            sql.executeUpdate("CREATE TRIGGER fail_reward BEFORE INSERT ON coin_transactions FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='failure'");
            fails(()->db.completeMatch("snapshot","done",false,2,results));
            check(number(c,"SELECT COUNT(*) FROM match_snapshots")==1,"Failed settlement removed snapshot");
            check(number(c,"SELECT coins FROM users WHERE username='alice'")==balance,"Partial reward");
            sql.executeUpdate("DROP TRIGGER fail_reward");
            ExecutorService pool=Executors.newFixedThreadPool(2);
            try { var a=pool.submit(()->db.completeMatch("snapshot","done",false,2,results));var b=pool.submit(()->db.completeMatch("snapshot","done",false,2,results));a.get();b.get(); }
            finally {pool.shutdownNow();}
            check(number(c,"SELECT COUNT(*) FROM coin_transactions WHERE reason='MATCH_REWARD'")==2,"Duplicate reward journal");
            check(number(c,"SELECT COUNT(*) FROM match_snapshots")==0,"Completed snapshot retained");
            fails(()->db.saveSnapshot("snapshot",new byte[]{4},2));
            check(number(c,"SELECT coins FROM users WHERE username='alice'")==balance+50,"Wrong reward");
            for(int repeat=0;repeat<2;repeat++)for(String statement:Files.readString(Path.of("docker/mysql/migrations/20261027-persistence.sql")).split(";")) {
                if(!statement.isBlank())sql.executeUpdate(statement);
            }
            new DatabaseStore();
            check(number(c,"SELECT COUNT(*) FROM direct_messages")==108,"Repeated migration changed messages");
        }
        Path oldDirectory=Files.createTempDirectory("partial-file-migration-");
        var oldUser=new GameServer.UserRecord("FileUser","hash");oldUser.coins=27;
        var staleAlice=new GameServer.UserRecord("alice","hash");staleAlice.coins=1;
        try(var out=new java.io.ObjectOutputStream(Files.newOutputStream(oldDirectory.resolve("state.bin")))) {
            out.writeObject(new HashMap<>(Map.of("fileuser",oldUser,"alice",staleAlice)));
        }
        Properties oldFriends=new Properties();oldFriends.setProperty("alice:fileuser","true");
        try(var out=Files.newOutputStream(oldDirectory.resolve("friends.properties"))){oldFriends.store(out,"legacy");}
        for(int repeat=0;repeat<2;repeat++)try(GameServer imported=new GameServer(0,oldDirectory)) { }
        try(Connection c=db.open()) {
            check(number(c,"SELECT coins FROM users WHERE username='fileuser'")==27,"Partial file import lost balance");
            check(number(c,"SELECT coins FROM users WHERE username='alice'")>1,"File import overwrote existing account");
            check(friends.friends("fileuser").equals(List.of("alice")),"File friendship import missing");
            check(friends.friends("alice").contains("FileUser"),"Legacy import lost display casing");
            check(number(c,"SELECT COUNT(*) FROM coin_transactions WHERE username='fileuser'")==0,"File import invented journal");
        }
        System.out.println("PERSISTENCE_EXTENSIONS_OK friendships migration chatPagination privacy receipts journalRollback duplicateRewards snapshotCAS");
    }
}
