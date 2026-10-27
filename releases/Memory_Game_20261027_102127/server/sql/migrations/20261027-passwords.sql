-- Additive and repeatable: preserves legacy SHA-256 until successful login.
ALTER TABLE users MODIFY password_hash VARCHAR(255) NOT NULL;

SET @history_index_ddl = (SELECT IF(COUNT(*)=0,
 'CREATE INDEX idx_results_history ON match_results(username, recorded_at DESC, legacy_order ASC, match_id DESC)',
 'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='match_results' AND INDEX_NAME='idx_results_history');
PREPARE history_index_statement FROM @history_index_ddl;
EXECUTE history_index_statement;
DEALLOCATE PREPARE history_index_statement;
