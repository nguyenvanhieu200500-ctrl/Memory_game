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
    static boolean tableExists(Connection c,String table)throws SQLException {
        try(ResultSet r=c.getMetaData().getTables(c.getCatalog(),null,table,new String[]{"TABLE"})){return r.next();}
    }
    static void migrateLegacyChat(DatabaseStore db)throws Exception {
        try(Connection c=db.open();Statement s=c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE private_messages (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, sender VARCHAR(16) NOT NULL, recipient VARCHAR(16) NOT NULL,
                    message_text VARCHAR(300) NOT NULL, sent_at BIGINT NOT NULL,
                    INDEX idx_private_sender(sender,id), INDEX idx_private_recipient(recipient,id),
                    FOREIGN KEY(sender) REFERENCES users(username) ON DELETE CASCADE,
                    FOREIGN KEY(recipient) REFERENCES users(username) ON DELETE CASCADE
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
            s.executeUpdate("INSERT INTO private_messages(id,sender,recipient,message_text,sent_at) VALUES (1001,'alice','bob','same text',700),(1002,'alice','bob','same text',700)");
            s.executeUpdate("INSERT INTO direct_messages(sender,recipient,message_text,sent_at,legacy_key) VALUES ('alice','bob','same text',700,'private_messages:1001')");
            s.executeUpdate("CREATE TRIGGER fail_direct_message BEFORE INSERT ON direct_messages FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected migration failure'");
            try { DirectMessagesMigration.migrate(c); throw new AssertionError("Expected injected migration failure"); }
            catch(SQLException expected) { check(expected.getMessage().contains("injected migration failure"),"Unexpected migration failure: "+expected.getMessage()); }
        }
        try(Connection c=db.open();Statement s=c.createStatement()) {
            check(tableExists(c,"private_messages"),"Failure removed legacy source");
            check(number(c,"SELECT COUNT(*) FROM private_messages")==2,"Failure changed source rows");
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE legacy_key IN ('private_messages:1001','private_messages:1002')")==1,"Failed transaction partially copied rows");
            s.executeUpdate("DROP TRIGGER fail_direct_message");
        }
        MigrateMain.main(new String[0]);
        try(Connection c=db.open()) {
            check(!tableExists(c,"private_messages"),"Validated migration did not drop source");
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE legacy_key IN ('private_messages:1001','private_messages:1002')")==2,"Identical messages collapsed or were lost");
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE legacy_key='private_messages:1002' AND sender='alice' AND recipient='bob' AND message_text='same text' AND read_at IS NULL AND sent_at=700")==1,"Migration changed message data or invented read time");
        }
        try(Connection c=db.open()) { DirectMessagesMigration.migrate(c); }
        new DatabaseStore();
        try(Connection c=db.open()) { check(!tableExists(c,"private_messages"),"Startup recreated removed source table"); }

        try(Connection c=db.open();Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE private_messages (id BIGINT PRIMARY KEY,sender VARCHAR(16) NOT NULL,recipient VARCHAR(16) NOT NULL,message_text VARCHAR(300) NOT NULL,sent_at BIGINT NOT NULL) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            s.executeUpdate("INSERT INTO private_messages VALUES (2001,'alice','bob','old body',800)");
            s.executeUpdate("INSERT INTO direct_messages(sender,recipient,message_text,sent_at,legacy_key) VALUES ('alice','bob','different body',800,'private_messages:2001')");
        }
        try(Connection c=db.open()) {
            try { DirectMessagesMigration.migrate(c); throw new AssertionError("Expected legacy_key conflict"); }
            catch(SQLException expected) { check(expected.getMessage().contains("conflicting direct_messages legacy_key"),"Conflict was not reported specifically"); }
        }
        try(Connection c=db.open();Statement s=c.createStatement()) {
            check(tableExists(c,"private_messages"),"Conflict removed legacy source");
            s.executeUpdate("DROP TABLE private_messages");
            s.executeUpdate("DELETE FROM direct_messages WHERE legacy_key='private_messages:2001'");
        }
        try(Connection c=db.open();Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE private_messages (id BIGINT PRIMARY KEY,sender VARCHAR(16) NOT NULL,recipient VARCHAR(16) NOT NULL,message_text VARCHAR(300) NOT NULL,sent_at BIGINT NOT NULL) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            s.executeUpdate("INSERT INTO private_messages VALUES (3001,'alice','alice','invalid self message',900)");
        }
        try(Connection c=db.open()) {
            try { DirectMessagesMigration.migrate(c); throw new AssertionError("Expected constraint validation failure"); }
            catch(SQLException expected) { check(expected.getMessage().contains("violate direct_messages constraints"),"Invalid source row was not reported specifically"); }
        }
        try(Connection c=db.open();Statement s=c.createStatement()) {
            check(tableExists(c,"private_messages"),"Invalid source data was dropped");
            s.executeUpdate("DROP TABLE private_messages");
        }
    }
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
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement("UPDATE friendships SET created_at=?,updated_at=? WHERE request_id=?")) {
            long old=System.currentTimeMillis()-180_000;p.setLong(1,old);p.setLong(2,old);p.setString(3,request);p.executeUpdate();
        }
        check(new FriendshipStore(new DatabaseStore()).pending("charlie").get(0).id().equals(request),"Pending lost on restart");
        check(friends.pending("charlie").get(0).sentAt()<=System.currentTimeMillis()-120_000,"Old pending invitation expired");
        friends.reply("charlie",request,true);friends.reply("charlie",request,true);
        String decline=friends.request("bob","dana");friends.reply("dana",decline,false);
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement("UPDATE friendships SET status='EXPIRED' WHERE request_id=?")) {
            p.setString(1,decline);p.executeUpdate();
        }
        check(friends.pending("dana").isEmpty(),"Previously expired invitation became pending again");
        String reverse=friends.request("dana","bob");friends.reply("bob",reverse,true);
        try(Connection c=db.open();Statement s=c.createStatement()) {
            check(number(c,"SELECT COUNT(*) FROM friendships")==3,"Duplicate pairs");
            check(number(c,"SELECT COUNT(*) FROM coin_transactions")==0,"Invented old coin history");
            s.executeUpdate("CREATE TRIGGER fail_friend BEFORE INSERT ON friendships FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='failure'");
            fails(()->friends.request("alice","dana"));
            s.executeUpdate("DROP TRIGGER fail_friend");
            check(friends.pending("dana").isEmpty(),"Failed invitation persisted");
        }
        new DatabaseStore();new DatabaseStore();
        migrateLegacyChat(db);
        Path source=Files.createTempDirectory("legacy-chat-").resolve("private-messages.bin");
        WhisperStore file=new WhisperStore(null,source);
        file.append("bob",new ChatEntry("alice","real file source",101));
        file.append("bob",new ChatEntry("alice","real file source",101));
        WhisperStore chat=new WhisperStore(db,source);new WhisperStore(db,source);
        for(int i=0;i<105;i++)chat.append(i%2==0?"bob":"alice",new ChatEntry(i%2==0?"alice":"bob","message "+i,1000+i));
        var first=chat.page("alice","bob",0);var second=chat.page("alice","bob",first.before());
        var third=chat.page("alice","bob",second.before());
        check(first.messages().size()==50 && second.messages().size()==50 && third.messages().size()==9 && !third.hasMore(),"Pagination loss/duplicate: "+first.messages().size()+"/"+second.messages().size()+"/"+third.messages().size()+" more="+third.hasMore());
        check(chat.page("charlie","bob",0).messages().isEmpty(),"Third party accessed history");
        chat.markRead("charlie","alice",Long.MAX_VALUE);
        try(Connection c=db.open();Statement sql=c.createStatement()) {
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE read_at IS NOT NULL")==0,"Third party read receipt");
            chat.markRead("bob","alice",first.newest());
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE recipient='bob' AND read_at IS NULL")==0,"Missing read receipts");
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE recipient='alice' AND read_at IS NOT NULL")==0,"Read receipt scope");
            check(number(c,"SELECT COUNT(*) FROM direct_messages WHERE legacy_key IS NOT NULL")==4,"Legacy SQL/file chat imported twice or lost duplicates");
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
            check(number(c,"SELECT COUNT(*) FROM direct_messages")==109,"Repeated migration changed messages");
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
