package memorygame.server;
/** Run with the migration account, then start ServerMain with the runtime account. */
public final class MigrateMain {
    public static void main(String[] args) {
        DatabaseStore database = new DatabaseStore(true);
        try (var connection = database.open()) {
            DirectMessagesMigration.migrate(connection);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Database migration failed; legacy chat source is retained when conversion validation fails: " + e.getMessage(), e);
        }
        System.out.println("Database migration completed");
    }
}
