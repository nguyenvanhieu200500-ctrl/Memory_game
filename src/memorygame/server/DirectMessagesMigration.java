package memorygame.server;

import java.sql.*;

/** One-time, restart-safe migration of legacy SQL chat rows into direct_messages. */
final class DirectMessagesMigration {
    private DirectMessagesMigration() {}

    static void migrate(Connection connection) throws SQLException {
        if (!tableExists(connection, "direct_messages"))
            throw new SQLException("direct_messages is missing; run the schema migration first");
        if (!tableExists(connection, "private_messages")) return;

        validateSource(connection);
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            try {
                validateSource(connection);
                validateKeyConflicts(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                        INSERT INTO direct_messages(sender,recipient,message_text,sent_at,legacy_key)
                        SELECT p.sender,p.recipient,p.message_text,p.sent_at,CONCAT('private_messages:',p.id)
                        FROM private_messages p
                        LEFT JOIN direct_messages d ON d.legacy_key=CONCAT('private_messages:',p.id)
                        WHERE d.id IS NULL
                        """);
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }

        // This check is deliberately after commit. The source table remains available on any failure.
        validateSource(connection);
        validateKeyConflicts(connection);
        long sourceRows = count(connection, "SELECT COUNT(*) FROM private_messages");
        long matchedRows = count(connection, """
            SELECT COUNT(*) FROM private_messages p
            JOIN direct_messages d ON d.legacy_key=CONCAT('private_messages:',p.id)
            WHERE BINARY d.legacy_key=BINARY CONCAT('private_messages:',p.id)
              AND BINARY d.sender=BINARY p.sender
              AND BINARY d.recipient=BINARY p.recipient
              AND BINARY d.message_text=BINARY p.message_text
              AND d.sent_at=p.sent_at
            """);
        if (sourceRows != matchedRows)
            throw new SQLException("Migration validation failed: source rows=" + sourceRows + ", matching destination rows=" + matchedRows + "; source table retained");

        // MySQL DDL implicitly commits. This is intentionally a separate final step after copy commit and validation.
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE private_messages");
        }
    }

    private static void validateSource(Connection c) throws SQLException {
        long invalid = count(c, """
            SELECT COUNT(*) FROM private_messages p
            LEFT JOIN users sender_user ON sender_user.username=p.sender
            LEFT JOIN users recipient_user ON recipient_user.username=p.recipient
            WHERE p.id IS NULL OR p.sender IS NULL OR p.recipient IS NULL OR p.message_text IS NULL OR p.sent_at IS NULL
               OR p.sender=p.recipient
               OR CHAR_LENGTH(p.message_text) NOT BETWEEN 1 AND 300
               OR p.sent_at<0
               OR sender_user.username IS NULL OR recipient_user.username IS NULL
               OR CHAR_LENGTH(CONCAT('private_messages:',p.id))>160
            """);
        if (invalid != 0)
            throw new SQLException("Legacy private_messages contains " + invalid + " row(s) that violate direct_messages constraints (users, distinct participants, message length, timestamp, or legacy_key length); source table retained");
    }

    private static void validateKeyConflicts(Connection c) throws SQLException {
        long conflicts = count(c, """
            SELECT COUNT(*) FROM private_messages p
            JOIN direct_messages d ON d.legacy_key=CONCAT('private_messages:',p.id)
            WHERE BINARY d.legacy_key<>BINARY CONCAT('private_messages:',p.id)
               OR BINARY d.sender<>BINARY p.sender
               OR BINARY d.recipient<>BINARY p.recipient
               OR BINARY d.message_text<>BINARY p.message_text
               OR d.sent_at<>p.sent_at
            """);
        if (conflicts != 0)
            throw new SQLException("Found " + conflicts + " conflicting direct_messages legacy_key value(s); source table retained");
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (ResultSet tables = c.getMetaData().getTables(c.getCatalog(), null, table, new String[]{"TABLE"})) {
            while (tables.next()) if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            return false;
        }
    }
}
