package memorygame.server;

import java.sql.*;
import java.util.*;

final class DatabaseStore {
    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3307/memory_game"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            + "&useUnicode=true&characterEncoding=UTF-8";

    private final String url;
    private final String user;
    private final String password;
    private Connection serverLease;

    DatabaseStore() { this(Boolean.parseBoolean(System.getenv("MEMORY_GAME_DB_MIGRATE"))); }

    DatabaseStore(boolean migrate) {
        url = environment("MEMORY_GAME_DB_URL", DEFAULT_URL);
        user = environment("MEMORY_GAME_DB_USER", "memory_game");
        password = System.getenv("MEMORY_GAME_DB_PASSWORD");
        if(password==null || password.isBlank())throw new IllegalStateException("Cần cấu hình MEMORY_GAME_DB_PASSWORD");
        if(migrate)initializeWithRetry();
    }

    Map<String, GameServer.UserRecord> loadUsers() {
        Map<String, GameServer.UserRecord> users = new HashMap<>();
        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT username, password_hash, rating, wins, games, coins,
                            equipped_theme, equipped_title, avatar
                     FROM users
                     """)) {
            while (rows.next()) {
                GameServer.UserRecord record = new GameServer.UserRecord(
                        rows.getString("username"), rows.getString("password_hash"));
                record.rating = rows.getInt("rating");
                record.wins = rows.getInt("wins");
                record.games = rows.getInt("games");
                record.coins = rows.getInt("coins");
                record.equippedTheme = rows.getString("equipped_theme");
                record.equippedTitle = rows.getString("equipped_title");
                record.avatar = rows.getString("avatar");
                users.put(key(record.username), record);
            }

            try (ResultSet items = statement.executeQuery(
                    "SELECT username, product_id FROM user_items ORDER BY username, product_id")) {
                while (items.next()) {
                    GameServer.UserRecord record = users.get(key(items.getString("username")));
                    if (record != null) record.ownedItems.add(items.getString("product_id"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể đọc dữ liệu MySQL: " + e.getMessage(), e);
        }

        return users;
    }

    void saveUsers(Map<String, GameServer.UserRecord> users) {
        String upsertUser = """
                INSERT INTO users
                    (username, password_hash, rating, wins, losses, games, coins,
                     equipped_theme, equipped_title, avatar)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)

                """;
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(upsertUser);
                 PreparedStatement insertItem = connection.prepareStatement(
                         "INSERT IGNORE INTO user_items(username, product_id) VALUES (?, ?)");
                 PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM users WHERE username=?");
                 PreparedStatement oldHistory = connection.prepareStatement("INSERT IGNORE INTO match_history(username,history_order,result_text) VALUES (?,?,?)");
                 PreparedStatement archiveProduct = connection.prepareStatement(
                         "INSERT IGNORE INTO products(product_id,name,description,price,product_type,color,active) VALUES (?,?,'Vật phẩm từ dữ liệu cũ',0,'LEGACY','#808080',FALSE)")) {
                boolean copiedLegacy = false;
                for (GameServer.UserRecord record : users.values()) {
                    exists.setString(1, record.username);
                    boolean fresh;
                    try(ResultSet row=exists.executeQuery()) { fresh=!row.next(); }
                    if (!fresh) continue; // Existing accounts are changed only by explicit operations, never RAM snapshots.
                    upsert.setString(1, record.username);
                    upsert.setString(2, record.passwordHash);
                    upsert.setInt(3, record.rating);
                    upsert.setInt(4, record.wins);
                    upsert.setInt(5, Math.max(0, record.games - record.wins));
                    upsert.setInt(6, record.games);
                    upsert.setInt(7, record.coins);
                    upsert.setString(8, record.equippedTheme);
                    upsert.setString(9, record.equippedTitle);
                    upsert.setString(10, record.avatar);
                    upsert.executeUpdate();
                    if (fresh && !record.history.isEmpty()) copiedLegacy = true;
                    if(fresh) for(int i=0;i<record.history.size();i++) {
                        oldHistory.setString(1,record.username); oldHistory.setInt(2,i); oldHistory.setString(3,record.history.get(i));
                        oldHistory.executeUpdate();
                    }

                    insertItem.clearBatch();
                    for (String product : record.ownedItems) {
                        archiveProduct.setString(1, product); archiveProduct.setString(2, product); archiveProduct.executeUpdate();
                        insertItem.setString(1, record.username);
                        insertItem.setString(2, product);
                        insertItem.addBatch();
                    }
                    insertItem.executeBatch();

                }
                if (copiedLegacy) GameSchema.copyHistory(connection);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể lưu dữ liệu MySQL: " + e.getMessage(), e);
        }
    }

    List<GameServer.ShopProduct> products(boolean activeOnly) {
        List<GameServer.ShopProduct> products = new ArrayList<>();
        try (Connection c = open(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT * FROM products" + (activeOnly ? " WHERE active=TRUE" : "") + " ORDER BY sort_order, product_id")) {
            while (r.next()) products.add(new GameServer.ShopProduct(r.getString("product_id"), r.getString("name"),
                    r.getString("description"), r.getInt("price"), r.getString("product_type"), r.getString("color")));
        } catch (SQLException e) { throw new IllegalStateException("Không đọc được cửa hàng", e); }
        return products;
    }

    /** Lock the account and product; debit and ownership either both commit or neither does. */
    String purchase(String username, String productId) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                int coins;
                try (PreparedStatement p = c.prepareStatement("SELECT coins FROM users WHERE username=? FOR UPDATE")) {
                    p.setString(1, username);
                    try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("Unknown account"); coins = r.getInt(1); }
                }
                if(operationExists(c,"purchase:"+key(username)+":"+productId)) {
                    c.rollback();return "Bạn đã sở hữu vật phẩm này.";
                }
                int price;
                try (PreparedStatement p = c.prepareStatement("SELECT price FROM products WHERE product_id=? AND active=TRUE AND product_type IN ('THEME','TITLE') FOR SHARE")) {
                    p.setString(1, productId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next() || r.getInt(1) <= 0) { c.rollback(); return "Vật phẩm không hợp lệ."; }
                        price = r.getInt(1);
                    }
                }
                try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM user_items WHERE username=? AND product_id=?")) {
                    p.setString(1, username); p.setString(2, productId);
                    try (ResultSet r = p.executeQuery()) { if (r.next()) { c.rollback(); return "Bạn đã sở hữu vật phẩm này."; } }
                }
                if (coins < price) { c.rollback(); return "Bạn chưa đủ xu để mua vật phẩm này."; }
                try (PreparedStatement p = c.prepareStatement("UPDATE users SET coins=coins-? WHERE username=?")) {
                    p.setInt(1, price); p.setString(2, username); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO user_items(username,product_id) VALUES (?,?)")) {
                    p.setString(1, username); p.setString(2, productId); p.executeUpdate();
                }
                recordCoins(c,"purchase:"+key(username)+":"+productId,username,-price,"PURCHASE",null);
                commit(c,fresh->operationExists(fresh,"purchase:"+key(username)+":"+productId)); return "";
            } catch (SQLException e) { rollback(c,e); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Không thể lưu giao dịch mua vật phẩm", e); }
    }

    static final class UnknownCommit extends SQLException {
        UnknownCommit(SQLException cause){super("Không xác định được kết quả commit; cần đối chiếu database",cause);}
    }
    static boolean unknownCommit(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof UnknownCommit)return true;
        return false;
    }
    @FunctionalInterface private interface Outcome { boolean committed(Connection connection) throws SQLException; }

    /** A broken COMMIT response is not proof of rollback. Resolve on a fresh connection first. */
    private void commit(Connection transaction,Outcome outcome) throws SQLException {
        try { transaction.commit(); }
        catch(SQLException uncertain) {
            try { transaction.close(); } catch(SQLException close) { uncertain.addSuppressed(close); }
            try(Connection fresh=open()) {
                if(outcome.committed(fresh))return;
            }catch(SQLException unavailable){uncertain.addSuppressed(unavailable);throw new UnknownCommit(uncertain);}
            throw uncertain; // Caller must not acknowledge success or publish tentative RAM.
        }
    }
    private static void rollback(Connection c,SQLException failure) {
        try { c.rollback(); }catch(SQLException rollback){failure.addSuppressed(rollback);}
    }
    private boolean operationExists(Connection c,String operation) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM coin_transactions WHERE operation_id=?")) {
            p.setString(1,operation);try(ResultSet r=p.executeQuery()){return r.next();}
        }
    }
    private boolean completed(Connection c,String id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
            SELECT 1 FROM matches m WHERE m.match_id=? AND m.status='COMPLETED'
              AND NOT EXISTS (SELECT 1 FROM match_snapshots s WHERE s.match_id=m.match_id)
              AND m.player_count=(SELECT COUNT(*) FROM match_results r JOIN coin_transactions t
                ON t.operation_id=CONCAT('reward:',r.match_id,':',LOWER(r.username))
                AND t.username=r.username AND t.amount=r.coin_reward AND t.match_id=r.match_id
                WHERE r.match_id=m.match_id AND r.score IS NOT NULL)
            """)) { p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next();} }
    }

    private void recordCoins(Connection c,String operation,String username,int amount,String reason,String match) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO coin_transactions(operation_id,username,amount,reason,match_id,created_at) VALUES (?,?,?,?,?,?)")) {
            p.setString(1,operation);p.setString(2,username);p.setInt(3,amount);p.setString(4,reason);
            p.setString(5,match);p.setLong(6,System.currentTimeMillis());p.executeUpdate();
        }
    }

    void beginMatch(String id, String room, int board, String theme, long started, List<String> players) {
        beginMatch(id,room,board,theme,started,players,null);
    }
    void beginMatch(String id, String room, int board, String theme, long started, List<String> players, byte[] snapshot) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO matches(match_id,room_code,board_size,card_theme,player_count,started_at,status) VALUES (?,?,?,?,?,?,'PLAYING')");
                 PreparedStatement participant = c.prepareStatement("INSERT INTO match_results(match_id,username,recorded_at) VALUES (?,?,?)")) {
                p.setString(1,id); p.setString(2,room); p.setInt(3,board); p.setString(4,theme);
                p.setInt(5,players.size()); p.setLong(6,started); p.executeUpdate();
                for (String name : players) { participant.setString(1,id); participant.setString(2,name); participant.setLong(3,started); participant.executeUpdate(); }
                if(snapshot!=null) writeSnapshot(c,id,snapshot);
                commit(c,fresh->{
                    try(PreparedStatement check=fresh.prepareStatement("SELECT 1 FROM matches WHERE match_id=? AND room_code=? AND started_at=? AND player_count=?")) {
                        check.setString(1,id);check.setString(2,room);check.setLong(3,started);check.setInt(4,players.size());
                        try(ResultSet r=check.executeQuery()){return r.next();}
                    }
                });
            } catch (SQLException e) { rollback(c,e); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Không thể lưu trận mới", e); }
    }

    /** A completed match is an idempotency key: retries never award it twice. */
    void completeMatch(String id, String reason, boolean abnormal, long ended, List<Map<String,Object>> results) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                int expected;
                try (PreparedStatement p = c.prepareStatement("SELECT status,player_count FROM matches WHERE match_id=? FOR UPDATE")) {
                    p.setString(1,id);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) throw new SQLException("Unknown match");
                        if ("COMPLETED".equals(r.getString(1))) { deleteSnapshot(c,id); c.commit(); return; }
                        if (!"PLAYING".equals(r.getString(1))) throw new SQLException("Invalid match status");
                        expected=r.getInt(2);
                    }
                }
                Set<String> names = new HashSet<>();
                for (var row : results) names.add(((String)row.get("username")).toLowerCase(Locale.ROOT));
                if (names.size()!=expected || results.size()!=expected) throw new SQLException("Incomplete results");
                // Stable lock order also allows concurrent transactions on different matches.
                List<Map<String,Object>> sorted = new ArrayList<>(results);
                sorted.sort(Comparator.comparing(row -> ((String)row.get("username")).toLowerCase(Locale.ROOT)));
                for (var row : sorted) {
                    String name=(String)row.get("username");
                    int delta=(Integer)row.get("ratingDelta"), coins=(Integer)row.get("coinReward"), before;
                    try (PreparedStatement p=c.prepareStatement("SELECT rating FROM users WHERE username=? FOR UPDATE")) {
                        p.setString(1,name);
                        try (ResultSet r=p.executeQuery()) { if(!r.next()) throw new SQLException("Unknown player"); before=r.getInt(1); }
                    }
                    try (PreparedStatement p=c.prepareStatement("""
                            UPDATE match_results SET score=?,placement=?,forfeited=?,winner=?,win_credit=?,coin_reward=?,
                            rating_delta=?,rating_applied=?,result_text=?,recorded_at=? WHERE match_id=? AND username=? AND score IS NULL
                            """)) {
                        p.setInt(1,(Integer)row.get("score")); p.setInt(2,(Integer)row.get("placement"));
                        p.setBoolean(3,(Boolean)row.get("forfeited")); p.setBoolean(4,(Boolean)row.get("winner"));
                        p.setBoolean(5,(Boolean)row.get("winCredit")); p.setInt(6,coins); p.setInt(7,delta);
                        p.setInt(8,Math.max(0,before+delta)-before); p.setString(9,(String)row.get("resultText"));
                        p.setLong(10,ended); p.setString(11,id); p.setString(12,name);
                        if(p.executeUpdate()!=1) throw new SQLException("Participant missing or already settled");
                    }
                    recordCoins(c,"reward:"+id+":"+key(name),name,coins,"MATCH_REWARD",id);
                    try (PreparedStatement p=c.prepareStatement("""
                            UPDATE users SET rating=GREATEST(0,rating+?), coins=coins+?, games=games+1,
                            wins=wins+?, losses=losses+? WHERE username=?
                            """)) {
                        int win=Boolean.TRUE.equals(row.get("winCredit"))?1:0;
                        p.setInt(1,delta); p.setInt(2,coins); p.setInt(3,win); p.setInt(4,1-win); p.setString(5,name); p.executeUpdate();
                    }
                }
                try (PreparedStatement p=c.prepareStatement("UPDATE matches SET status='COMPLETED',ended_at=?,reason=?,abnormal=? WHERE match_id=?")) {
                    p.setLong(1,ended); p.setString(2,reason); p.setBoolean(3,abnormal); p.setString(4,id); p.executeUpdate();
                }
                deleteSnapshot(c,id);
                commit(c,fresh->completed(fresh,id));
            } catch (SQLException e) { rollback(c,e); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Không thể lưu kết quả trận đấu",e); }
    }

    List<String> history(String username) {
        List<String> history=new ArrayList<>();
        try (Connection c=open(); PreparedStatement p=c.prepareStatement("""
                SELECT result_text FROM (
                    (SELECT r.result_text, r.recorded_at AS history_time, r.legacy_order, r.match_id AS ordering_id
                    FROM match_results r JOIN matches m ON m.match_id=r.match_id
                    WHERE r.username=? AND m.status IN ('COMPLETED','INTERRUPTED','LEGACY') AND r.result_text IS NOT NULL
                    ORDER BY r.recorded_at DESC,r.legacy_order ASC,r.match_id DESC LIMIT 50)
                    UNION ALL
                    (SELECT h.result_text, UNIX_TIMESTAMP(h.played_at)*1000, h.history_order, CONCAT('legacy-',h.id)
                    FROM match_history h WHERE h.username=? AND NOT EXISTS
                        (SELECT 1 FROM match_results r WHERE r.legacy_history_id=h.id AND r.result_text IS NOT NULL)
                    ORDER BY h.played_at DESC,h.history_order ASC,CONCAT('legacy-',h.id) DESC LIMIT 50)
                ) combined ORDER BY history_time DESC, legacy_order ASC, ordering_id DESC LIMIT 50
                """)) {
            p.setString(1,username); p.setString(2,username);
            try(ResultSet r=p.executeQuery()) { while(r.next()) history.add(r.getString(1)); }
        } catch(SQLException e) { throw new IllegalStateException("Không đọc được lịch sử",e); }
        return history;
    }

    void updateAvatar(String username, String avatar) {
        updateAccount("UPDATE users SET avatar=? WHERE username=?", avatar, username);
    }
    void updatePassword(String username, String previousHash, String passwordHash) {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("UPDATE users SET password_hash=? WHERE username=? AND BINARY password_hash=?")) {
            p.setString(1,passwordHash);p.setString(2,username);p.setString(3,previousHash);
            if(p.executeUpdate()!=1 && !passwordIs(username,passwordHash)) throw new SQLException("Mật khẩu đã thay đổi; hãy đăng nhập lại");
        } catch(SQLException e){
            try{if(passwordIs(username,passwordHash))return;}catch(SQLException unavailable){e.addSuppressed(unavailable);}
            throw new IllegalStateException("Không lưu được mật khẩu",e);
        }
    }
    private boolean passwordIs(String username,String expected) throws SQLException {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT 1 FROM users WHERE username=? AND BINARY password_hash=?")) {
            p.setString(1,username);p.setString(2,expected);try(ResultSet r=p.executeQuery()){return r.next();}
        }
    }
    void updateEquipment(String username, String productId, boolean theme) {
        String column=theme?"equipped_theme":"equipped_title";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(
                "UPDATE users u JOIN products p ON p.product_id=? LEFT JOIN user_items i ON i.username=u.username AND i.product_id=p.product_id "
                + "SET u."+column+"=p.product_id WHERE u.username=? AND p.product_type=? AND ((p.active=TRUE AND p.price=0) OR i.product_id IS NOT NULL)")) {
            p.setString(1,productId);p.setString(2,username);p.setString(3,theme?"THEME":"TITLE");
            if(p.executeUpdate()!=1) throw new SQLException("Vật phẩm không thuộc người chơi");
        } catch(SQLException e){throw new IllegalStateException("Không lưu được trang bị",e);}
    }
    private void updateAccount(String sql, String value, String username) {
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,value);p.setString(2,username);
            if(p.executeUpdate()!=1) throw new SQLException("Unknown account");
        } catch(SQLException e) { throw new IllegalStateException("Không lưu được tài khoản",e); }
    }

    List<GameServer.ShopProduct> productsFor(String username) {
        Set<String> owned=new HashSet<>();
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT product_id FROM user_items WHERE username=?")) {
            p.setString(1,username);try(ResultSet r=p.executeQuery()){while(r.next())owned.add(r.getString(1));}
        } catch(SQLException e){throw new IllegalStateException("Không đọc được vật phẩm sở hữu",e);}
        Set<String> active=new HashSet<>();products(true).forEach(p->active.add(p.id()));
        return products(false).stream().filter(p->active.contains(p.id()) || owned.contains(p.id())).toList();
    }

    /** Only changes PLAYING records. No statistics or wallet changes for interruption. */
    void interruptMatch(String id, long ended, String reason, Map<String,Integer> scores, Set<String> forfeited) {
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                long started;
                try(PreparedStatement p=c.prepareStatement("SELECT status,started_at FROM matches WHERE match_id=? FOR UPDATE")) {
                    p.setString(1,id);try(ResultSet r=p.executeQuery()) {
                        if(!r.next() || !"PLAYING".equals(r.getString(1))){c.rollback();return;}
                        started=r.getLong(2);
                    }
                }
                ended=Math.max(ended,started);
                List<String> names=new ArrayList<>();
                try(PreparedStatement p=c.prepareStatement("SELECT username FROM match_results WHERE match_id=?")) {
                    p.setString(1,id);try(ResultSet r=p.executeQuery()){while(r.next())names.add(r.getString(1));}
                }
                for(String name:names) {
                    Integer score=scores.get(name);
                    String stamp=java.time.Instant.ofEpochMilli(ended).atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                    String text=stamp+" | "+reason+" | "+(score==null?"Chưa lưu số cặp":score+" cặp")+" | Gián đoạn | Xếp hạng 0 | +0 xu";
                    try(PreparedStatement p=c.prepareStatement("""
                        UPDATE match_results SET score=COALESCE(?,score),forfeited=COALESCE(?,forfeited),winner=FALSE,
                        win_credit=FALSE,coin_reward=0,rating_delta=0,rating_applied=0,result_text=?,recorded_at=? WHERE match_id=? AND username=?
                        """)) {
                        if(score==null)p.setNull(1,Types.INTEGER);else p.setInt(1,score);
                        if(score==null)p.setNull(2,Types.BOOLEAN);else p.setBoolean(2,forfeited.contains(name));
                        p.setString(3,text);p.setLong(4,ended);p.setString(5,id);p.setString(6,name);p.executeUpdate();
                    }
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE matches SET status='INTERRUPTED',ended_at=?,reason=?,abnormal=TRUE WHERE match_id=?")) {
                    p.setLong(1,ended);p.setString(2,reason);p.setString(3,id);p.executeUpdate();
                }
                deleteSnapshot(c,id);
                c.commit();
            } catch(SQLException e){rollback(c,e);throw e;}
        } catch(SQLException e){throw new IllegalStateException("Không ghi được trạng thái trận gián đoạn",e);}
    }

    record StoredSnapshot(String id, String room, int board, String theme, long started,
                          int playerCount, Set<String> players, int version, long revision, byte[] payload, String checksum) {}

    void acquireServerLease() {
        try {
            serverLease=open();
            try(Statement s=serverLease.createStatement();ResultSet r=s.executeQuery("SELECT GET_LOCK(CONCAT('memorygame:',LEFT(SHA2(DATABASE(),256),40)),0)")) {
                if(!r.next() || r.getInt(1)!=1) throw new SQLException("Một server khác đang sử dụng database này");
            }
        } catch(SQLException e){releaseServerLease();throw new IllegalStateException("Không giữ được quyền quản lý ván",e);}
    }
    void releaseServerLease() {
        if(serverLease!=null) try {serverLease.close();}catch(SQLException ignored){} finally {serverLease=null;}
    }
    void heartbeatServerLease() {
        try(Statement s=serverLease.createStatement();ResultSet r=s.executeQuery("SELECT IS_USED_LOCK(CONCAT('memorygame:',LEFT(SHA2(DATABASE(),256),40)))=CONNECTION_ID()")) {
            if(!r.next() || !r.getBoolean(1)) throw new SQLException("Mất khóa server");
        } catch(SQLException e){throw new IllegalStateException("Mất kết nối giữ quyền quản lý ván",e);}
    }
    static String checksum(byte[] payload) {
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void writeSnapshot(Connection c,String id,byte[] payload) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
            INSERT INTO match_snapshots(match_id,format_version,payload,checksum,saved_at) VALUES (?,1,?,?,?)
            ON DUPLICATE KEY UPDATE format_version=1,revision=revision+1,payload=VALUES(payload),checksum=VALUES(checksum),saved_at=VALUES(saved_at)
            """)) {
            p.setString(1,id);p.setBytes(2,payload);p.setString(3,checksum(payload));p.setLong(4,System.currentTimeMillis());p.executeUpdate();
        }
    }
    private void deleteSnapshot(Connection c,String id) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("DELETE FROM match_snapshots WHERE match_id=?")){p.setString(1,id);p.executeUpdate();}
    }
    long saveSnapshot(String id,byte[] payload,long expectedRevision) {
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("SELECT status FROM matches WHERE match_id=? FOR UPDATE")) {
                    p.setString(1,id);try(ResultSet r=p.executeQuery()) {
                        if(!r.next() || !"PLAYING".equals(r.getString(1)))throw new SQLException("Trận không còn đang chơi");
                    }
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE match_snapshots SET revision=revision+1,payload=?,checksum=?,saved_at=? WHERE match_id=? AND revision=?")) {
                    p.setBytes(1,payload);p.setString(2,checksum(payload));p.setLong(3,System.currentTimeMillis());p.setString(4,id);p.setLong(5,expectedRevision);
                    if(p.executeUpdate()!=1)throw new SQLException("Snapshot revision conflict");
                }
                commit(c,fresh->{
                    try(PreparedStatement p=fresh.prepareStatement("SELECT 1 FROM match_snapshots WHERE match_id=? AND revision=? AND checksum=?")) {
                        p.setString(1,id);p.setLong(2,expectedRevision+1);p.setString(3,checksum(payload));
                        try(ResultSet r=p.executeQuery()){return r.next();}
                    }
                });return expectedRevision+1;
            }catch(SQLException e){rollback(c,e);throw e;}
        }catch(SQLException e){throw new IllegalStateException("Không lưu được snapshot",e);}
    }
    List<StoredSnapshot> playingSnapshots() {
        List<StoredSnapshot> result=new ArrayList<>();
        try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("""
            SELECT m.*,s.format_version,s.revision,s.payload,s.checksum FROM matches m LEFT JOIN match_snapshots s ON m.match_id=s.match_id
            WHERE m.status='PLAYING' ORDER BY m.started_at,m.match_id
            """)) {
            while(r.next()) {
                Set<String> players=new HashSet<>();
                try(PreparedStatement p=c.prepareStatement("SELECT username FROM match_results WHERE match_id=?")) {
                    p.setString(1,r.getString("match_id"));try(ResultSet names=p.executeQuery()){while(names.next())players.add(names.getString(1));}
                }
                result.add(new StoredSnapshot(r.getString("match_id"),r.getString("room_code"),r.getInt("board_size"),r.getString("card_theme"),
                        r.getLong("started_at"),r.getInt("player_count"),players,r.getInt("format_version"),r.getLong("revision"),r.getBytes("payload"),r.getString("checksum")));
            }
        }catch(SQLException e){throw new IllegalStateException("Không đọc được snapshot",e);}
        return result;
    }

    void recoverInterruptedMatches() {
        List<String> ids=new ArrayList<>();
        try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT m.match_id FROM matches m LEFT JOIN match_snapshots s ON s.match_id=m.match_id WHERE m.status='PLAYING' AND s.match_id IS NULL")) {
            while(r.next()) ids.add(r.getString(1));
        } catch(SQLException e){throw new IllegalStateException("Không đọc được trận chưa kết thúc",e);}
        for(String id:ids) interruptMatch(id,System.currentTimeMillis(),"Server dừng trước khi lưu kết quả; phát hiện khi khởi động lại",Map.of(),Set.of());
    }

    private void initializeWithRetry() {
        Exception last = null;
        for (int attempt = 1; attempt <= 45; attempt++) {
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS users (
                            username VARCHAR(16) PRIMARY KEY,
                            password_hash VARCHAR(255) NOT NULL,
                            rating INT NOT NULL DEFAULT 0,
                            wins INT NOT NULL DEFAULT 0,
                            losses INT NOT NULL DEFAULT 0,
                            games INT NOT NULL DEFAULT 0,
                            coins INT NOT NULL DEFAULT 0,
                            equipped_theme VARCHAR(64) NOT NULL DEFAULT 'theme_classic',
                            equipped_title VARCHAR(64) NOT NULL DEFAULT '',
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            INDEX idx_ranking (rating DESC, wins DESC)
                        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                        """);
                try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "users", "avatar")) {
                    if (!columns.next()) statement.executeUpdate("ALTER TABLE users ADD COLUMN avatar MEDIUMTEXT NULL");
                }
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS user_items (
                            username VARCHAR(16) NOT NULL,
                            product_id VARCHAR(64) NOT NULL,
                            purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (username, product_id),
                            CONSTRAINT fk_items_user FOREIGN KEY (username) REFERENCES users(username)
                                ON DELETE CASCADE
                        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS match_history (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            username VARCHAR(16) NOT NULL,
                            history_order INT NOT NULL,
                            result_text VARCHAR(500) NOT NULL,
                            played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            UNIQUE KEY uk_history_position (username, history_order),
                            CONSTRAINT fk_history_user FOREIGN KEY (username) REFERENCES users(username)
                                ON DELETE CASCADE,
                            INDEX idx_history_user_time (username, played_at DESC)
                        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                        """);
                statement.executeUpdate("""
                        CREATE OR REPLACE VIEW leaderboard AS
                        SELECT username, rating, wins, losses, games, coins,
                               CASE WHEN games = 0 THEN 0
                                    ELSE ROUND(wins * 100.0 / games, 2) END AS win_rate
                        FROM users
                        """);
                statement.executeUpdate(WhisperStore.SCHEMA);
                GameSchema.upgrade(connection);
                return;
            } catch (Exception e) {
                if (e instanceof SQLException sql && sql.getErrorCode() != 0
                        && sql.getErrorCode() != 1040 && sql.getErrorCode() != 1205 && sql.getErrorCode() != 1213) {
                    throw new IllegalStateException("Không thể nâng cấp MySQL; dữ liệu hoặc schema không hợp lệ: " + e.getMessage(), e);
                }
                last = e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("Không thể kết nối MySQL sau 45 giây: "
                + (last == null ? "không rõ nguyên nhân" : last.getMessage()), last);
    }

    Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
