package memorygame.server;

import java.sql.*;
import java.nio.file.*;
import java.util.*;

public final class SchemaParityTest {
    static Set<String> signature(Connection c)throws SQLException {
        Set<String> result=new TreeSet<>();
        for(String query:List.of(
            "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()",
            "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,COLLATION FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()",
            "SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL",
            "SELECT t.TABLE_NAME,c.CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS c JOIN information_schema.TABLE_CONSTRAINTS t ON t.CONSTRAINT_SCHEMA=c.CONSTRAINT_SCHEMA AND t.CONSTRAINT_NAME=c.CONSTRAINT_NAME WHERE t.CONSTRAINT_SCHEMA=DATABASE()")) {
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery(query)) {
                while(r.next()){StringJoiner row=new StringJoiner("|");for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.add(String.valueOf(r.getString(i)).toLowerCase(Locale.ROOT).replaceAll("[\\s()]", ""));result.add(row.toString());}
            }
        }
        return result;
    }
    public static void main(String[] args)throws Exception {
        try(Connection c=DriverManager.getConnection(System.getenv("MEMORY_GAME_DB_URL"),System.getenv("MEMORY_GAME_DB_USER"),System.getenv("MEMORY_GAME_DB_PASSWORD"));Statement s=c.createStatement()) {
            for(String query:Files.readString(Path.of("docker/mysql/init.sql")).split(";")) {
                query=query.strip();if(query.isEmpty()||query.startsWith("CREATE DATABASE")||query.startsWith("USE "))continue;s.execute(query);
            }
            Set<String> before=signature(c);
            new DatabaseStore();
            Set<String> after=signature(c);
            Set<String> added=new TreeSet<>(after);added.removeAll(before);Set<String> removed=new TreeSet<>(before);removed.removeAll(after);
            if(!added.isEmpty()||!removed.isEmpty())throw new AssertionError("INIT/MIGRATION DIFFER: added="+added+" removed="+removed);
            for(int pass=0;pass<3;pass++) {
                for(String file:List.of("docker/mysql/migrations/20261027-persistence.sql","docker/mysql/migrations/20261027-passwords.sql"))for(String query:Files.readString(Path.of(file)).split(";"))if(!query.isBlank())s.execute(query);
                new DatabaseStore();
            }
            if(!signature(c).equals(after))throw new AssertionError("Repeated migrations changed schema");
        }
        System.out.println("SCHEMA_PARITY_OK columns indexes foreignKeys checks SQLBootstrap runtimeUpgrade repeatedSQLMigration");
    }
}
