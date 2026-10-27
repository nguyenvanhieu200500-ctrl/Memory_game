package memorygame.server;
/** Run with the migration account, then start ServerMain with the runtime account. */
public final class MigrateMain {
    public static void main(String[] args) { new DatabaseStore(true); System.out.println("Database migration completed"); }
}
