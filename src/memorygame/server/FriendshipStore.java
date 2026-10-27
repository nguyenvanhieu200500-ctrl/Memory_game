package memorygame.server;

import java.sql.*;
import java.util.*;

/** Database is authoritative; no successful response precedes the commit. */
final class FriendshipStore {
    private final DatabaseStore database;
    FriendshipStore(DatabaseStore database) { this.database=database; }
    record Invitation(String id,String sender,String recipient,long sentAt) {}
    record Reply(String sender,boolean changed) {}

    void importLegacy(Properties friends) {
        try(Connection c=database.open()) {
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO friendships(sender,recipient,request_id,status,created_at,updated_at)
                VALUES (?,?,?,'ACCEPTED',NULL,?)
                ON DUPLICATE KEY UPDATE status='ACCEPTED'
                """)) {
                for(String pair:friends.stringPropertyNames()) {
                    String[] names=pair.split(":",-1);
                    if(names.length!=2 || names[0].equalsIgnoreCase(names[1])) throw new SQLException("Quan hệ cũ không hợp lệ: "+pair);
                    p.setString(1,names[0]);p.setString(2,names[1]);p.setString(3,"legacy:"+pair);
                    p.setLong(4,System.currentTimeMillis());p.executeUpdate();
                }
                c.commit();
            } catch(SQLException e) { c.rollback();throw e; }
        } catch(SQLException e) { throw new IllegalStateException("Không chuyển được bạn bè cũ; giữ nguyên tệp nguồn",e); }
    }
    List<String> friends(String account) {
        List<String> names=new ArrayList<>();
        try(Connection c=database.open();PreparedStatement p=c.prepareStatement("SELECT a.username,b.username FROM friendships f JOIN users a ON a.username=f.sender JOIN users b ON b.username=f.recipient WHERE f.status='ACCEPTED' AND (f.sender=? OR f.recipient=?)")) {
            p.setString(1,account);p.setString(2,account);
            try(ResultSet r=p.executeQuery()) { while(r.next()) names.add(account.equalsIgnoreCase(r.getString(1))?r.getString(2):r.getString(1)); }
        }catch(SQLException e){throw new IllegalStateException("Không đọc được bạn bè",e);}
        return names;
    }
    String request(String sender,String recipient) {
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        try(Connection c=database.open()) {
            c.setAutoCommit(false);
            try {
                // Lock both users in a stable order, including when no relationship exists yet.
                for(String name:new TreeSet<>(List.of(sender,recipient)))try(PreparedStatement p=c.prepareStatement("SELECT username FROM users WHERE username=? FOR UPDATE")) {
                    p.setString(1,name);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Unknown user");}
                }
                try(PreparedStatement p=c.prepareStatement("SELECT status FROM friendships WHERE pair_low=LEAST(?,?) AND pair_high=GREATEST(?,?) FOR UPDATE")) {
                    p.setString(1,sender);p.setString(2,recipient);p.setString(3,sender);p.setString(4,recipient);
                    try(ResultSet r=p.executeQuery()) { if(r.next() && (r.getString(1).equals("ACCEPTED") || r.getString(1).equals("PENDING"))) throw new SQLException("Đã là bạn bè hoặc đã có lời mời đang chờ"); }
                }
                try(PreparedStatement p=c.prepareStatement("""
                    INSERT INTO friendships(sender,recipient,request_id,status,created_at,updated_at) VALUES (?,?,?,'PENDING',?,?)
                    ON DUPLICATE KEY UPDATE sender=VALUES(sender),recipient=VALUES(recipient),request_id=VALUES(request_id),status='PENDING',created_at=VALUES(created_at),updated_at=VALUES(updated_at)
                    """)) {
                    p.setString(1,sender);p.setString(2,recipient);p.setString(3,id);p.setLong(4,now);p.setLong(5,now);p.executeUpdate();
                }
                c.commit();return id;
            }catch(SQLException e){c.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("Không gửi được lời mời kết bạn: "+e.getMessage(),e);}
    }
    List<Invitation> pending(String recipient) {
        List<Invitation> result=new ArrayList<>();
        try(Connection c=database.open()) {
            try(PreparedStatement p=c.prepareStatement("SELECT request_id,sender,recipient,created_at FROM friendships WHERE recipient=? AND status='PENDING' ORDER BY created_at,request_id")) {
                p.setString(1,recipient);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new Invitation(r.getString(1),r.getString(2),r.getString(3),r.getLong(4)));}
            }
        }catch(SQLException e){throw new IllegalStateException("Không đọc được lời mời",e);}
        return result;
    }
    Reply reply(String recipient,String id,boolean accepted) {
        try(Connection c=database.open()) {
            c.setAutoCommit(false);
            try {
                String sender,status;
                try(PreparedStatement p=c.prepareStatement("SELECT sender,status FROM friendships WHERE request_id=? AND recipient=? FOR UPDATE")) {
                    p.setString(1,id);p.setString(2,recipient);
                    try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Lời mời không tồn tại");sender=r.getString(1);status=r.getString(2);}
                }
                String desired=accepted?"ACCEPTED":"DECLINED";
                if(status.equals(desired)){c.commit();return new Reply(sender,false);}
                if(!status.equals("PENDING"))throw new SQLException("Lời mời đã được xử lý");
                try(PreparedStatement p=c.prepareStatement("UPDATE friendships SET status=?,updated_at=? WHERE request_id=?")) {
                    p.setString(1,desired);p.setLong(2,System.currentTimeMillis());p.setString(3,id);p.executeUpdate();
                }
                c.commit();return new Reply(sender,true);
            }catch(SQLException e){c.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("Không lưu được phản hồi kết bạn",e);}
    }
}
