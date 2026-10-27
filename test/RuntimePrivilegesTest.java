package memorygame.server;

import java.sql.*;
import java.util.*;

public final class RuntimePrivilegesTest {
    public static void main(String[] args)throws Exception {
        DatabaseStore db=new DatabaseStore();String name="game_rt_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        String secret=UUID.randomUUID().toString();
        try(Connection c=db.open();Statement s=c.createStatement()) {
            String schema=c.getCatalog();if(!schema.matches("game_test_[a-f0-9]{32}"))throw new SecurityException("Test schema only");
            s.execute("CREATE USER '"+name+"'@'%' IDENTIFIED BY '"+secret+"'");
            try {
                s.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON "+schema+".* TO '"+name+"'@'%'");
                try(Connection runtime=DriverManager.getConnection(System.getenv("MEMORY_GAME_DB_URL"),name,secret);Statement statement=runtime.createStatement()) {
                    try{statement.execute("ALTER TABLE users ADD COLUMN forbidden_test INT");throw new AssertionError("Runtime DDL allowed");}catch(SQLException expected){if(expected.getErrorCode()!=1142)throw expected;}
                }
                ProcessBuilder child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),"memorygame.test.ProfileTest");
                child.environment().put("MEMORY_GAME_DB_USER",name);child.environment().put("MEMORY_GAME_DB_PASSWORD",secret);child.environment().put("MEMORY_GAME_DB_MIGRATE","false");
                if(child.inheritIO().start().waitFor()!=0)throw new AssertionError("Runtime needs DDL privileges");
            }finally{s.execute("DROP USER '"+name+"'@'%'");}
        }
        System.out.println("RUNTIME_PRIVILEGES_OK appWithoutDDL migrationsSeparate disposableAccountRemoved");
    }
}
