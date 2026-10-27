package memorygame.test;

import memorygame.common.Protocol.*;
import memorygame.server.*;
import java.nio.file.*;

public final class PasswordMigrationTest {
    public static void main(String[] args)throws Exception {
        MigrateMain.main(new String[0]);
        try(var c=GamePersistenceProtocolTest.connection();var sql=c.createStatement()) {
            sql.executeUpdate("INSERT INTO users(username,password_hash,coins) VALUES ('legacy_auth',SHA2('legacy123',256),127)");
            sql.executeUpdate("CREATE TRIGGER fail_upgrade BEFORE UPDATE ON users FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure'");
        }
        Path dir=Files.createTempDirectory("password-upgrade-");GameServer server=new GameServer(ProfileTest.PORT,dir);Thread thread=ProfileTest.start(server);
        try(var a=new ProfileTest.Client();var c=GamePersistenceProtocolTest.connection();var sql=c.createStatement()) {
            a.send(Message.of(Type.LOGIN).with("username","legacy_auth").with("password","wrong123"));a.await(Type.ERROR);
            a.send(Message.of(Type.LOGIN).with("username","legacy_auth").with("password","legacy123"));a.await(Type.ERROR);
            GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT LENGTH(password_hash) FROM users WHERE username='legacy_auth'")==64,"Failed migration damaged legacy hash");
            sql.executeUpdate("DROP TRIGGER fail_upgrade");
            a.send(Message.of(Type.LOGIN).with("username","legacy_auth").with("password","legacy123"));a.await(Type.LOGIN_OK);
            GameRecoveryTest.check(ServerInterruptionTest.number(c,"SELECT COUNT(*) FROM users WHERE password_hash LIKE 'pbkdf2-sha256$600000$%' AND coins=127")==1,"Legacy login not upgraded");
            a.send(Message.of(Type.PASSWORD_CHANGE).with("currentPassword","legacy123").with("newPassword","modern123"));ProfileTest.success(a.await(Type.PROFILE_RESULT),true);
        }finally{server.close();thread.join(2000);}
        server=new GameServer(ProfileTest.PORT,dir);thread=ProfileTest.start(server);
        try(var a=new ProfileTest.Client()) {
            a.send(Message.of(Type.LOGIN).with("username","legacy_auth").with("password","legacy123"));a.await(Type.ERROR);
            a.send(Message.of(Type.LOGIN).with("username","legacy_auth").with("password","modern123"));a.await(Type.LOGIN_OK);
        }finally{server.close();thread.join(2000);}
        System.out.println("PASSWORD_MIGRATION_OK oldLogin wrongPassword failedUpgrade preservesBalance modernLogin passwordChange restart");
    }
}
