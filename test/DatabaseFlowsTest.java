package memorygame.test;

import java.sql.*;
import java.util.UUID;

/** Owns only a randomly named temporary schema. Production game data is untouched. */
public final class DatabaseFlowsTest {
    public static void main(String[] args) throws Exception {
        String schema="game_test_"+UUID.randomUUID().toString().replace("-", "");
        String base="jdbc:mysql://127.0.0.1:3307/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try(Connection c=DriverManager.getConnection(base+options,"root","memory_game_root_123");var s=c.createStatement()) {
            s.executeUpdate("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                String test=args.length==0?"memorygame.server.GameDatabaseTest":args[0];
                ProcessBuilder child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),test);
                child.environment().put("MEMORY_GAME_STORAGE","mysql");child.environment().put("MEMORY_GAME_DB_URL",base+schema+options);
                child.environment().put("MEMORY_GAME_DB_USER","root");child.environment().put("MEMORY_GAME_DB_PASSWORD","memory_game_root_123");
                if(child.inheritIO().start().waitFor()!=0) throw new AssertionError("Isolated database test failed");
            } finally {s.executeUpdate("DROP DATABASE "+schema);}
        }
    }
}
