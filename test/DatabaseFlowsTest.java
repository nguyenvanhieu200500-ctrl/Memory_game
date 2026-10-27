package memorygame.test;

import java.sql.*;
import java.util.UUID;

/** Owns only a randomly named temporary schema. Production game data is untouched. */
public final class DatabaseFlowsTest {
    public static void main(String[] args) throws Exception {
        String password=System.getenv("MEMORY_GAME_TEST_DB_PASSWORD");
        if(password==null || password.isBlank())throw new IllegalArgumentException("Set MEMORY_GAME_TEST_DB_PASSWORD for the local test MySQL root account");
        String schema="game_test_"+UUID.randomUUID().toString().replace("-", "");
        String base="jdbc:mysql://127.0.0.1:3307/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try(Connection c=DriverManager.getConnection(base+options,"root",password);var s=c.createStatement()) {
            s.executeUpdate("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                String test=args.length==0?"memorygame.server.GameDatabaseTest":args[0];
                ProcessBuilder child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),test);
                child.environment().put("MEMORY_GAME_DB_MIGRATE","true");
                child.environment().put("MEMORY_GAME_STORAGE","mysql");child.environment().put("MEMORY_GAME_DB_URL",base+schema+options);
                child.environment().put("MEMORY_GAME_DB_USER","root");child.environment().put("MEMORY_GAME_DB_PASSWORD",password);
                if(child.inheritIO().start().waitFor()!=0) throw new AssertionError("Isolated database test failed");
            } finally {s.executeUpdate("DROP DATABASE "+schema);}
        }
    }
}
