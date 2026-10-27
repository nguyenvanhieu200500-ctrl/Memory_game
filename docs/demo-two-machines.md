# Demo Memory Game trên hai máy

Đây là kịch bản để thực hiện trên hai máy vật lý, **chưa phải biên bản đã chạy hai máy**. Kiểm thử tự động của đợt chuẩn bị demo dùng các socket/JVM riêng trên cùng một máy macOS. Dùng bản source hiện tại trên cả hai máy; biên dịch lại, không chạy client cũ trong `dist/`.

## Chuẩn bị

- Máy A: Java 17+, MySQL Docker, server và client A. Máy B: Java 17+ và client B; không cần MySQL/JDBC hoặc mật khẩu database.
- Cùng mạng LAN; máy B nhập IPv4 LAN của máy A và cổng 12345. Cho phép TCP 12345 trên firewall máy A. Client không kết nối trực tiếp cổng 3307.
- Dành một database `memory_game_demo` riêng cho phần tắt cưỡng bức. Quản trị viên tạo database rỗng, chạy migration theo [database.md](database.md#áp-dụng) với URL trỏ đến database này, rồi cấp tài khoản runtime SELECT/INSERT/UPDATE/DELETE chỉ trên `memory_game_demo.*`. Không thực hiện thử lỗi trên database đang dùng.
- Đặt `MEMORY_GAME_DATA_DIR` tới thư mục riêng không có `state.bin`, `friends.properties` hoặc `private-messages.bin` cũ, tránh nhập tài khoản thật vào demo. Server mặc định vẫn dùng `data/` để tương thích dữ liệu cũ. Biên dịch tại thư mục dự án trước; ví dụ macOS/Linux:

```sh
# Trong thư mục dự án, sau khi migration database demo hoàn tất:
sh compile.sh
DEMO_PROJECT="$PWD"
mkdir -p /tmp/memory-game-demo-session
export MEMORY_GAME_DATA_DIR=/tmp/memory-game-demo-session/data
cd /tmp/memory-game-demo-session
# Đã đặt DB_URL tới memory_game_demo, DB_USER/DB_PASSWORD của runtime trong terminal này.
export MEMORY_GAME_STORAGE=mysql MEMORY_GAME_DB_MIGRATE=false
java -cp "$DEMO_PROJECT/out:$DEMO_PROJECT/lib/*" memorygame.server.ServerMain 12345 --headless > server-demo.log 2>&1 &
DEMO_SERVER_PID=$!
```

Trên Windows, đặt `set MEMORY_GAME_DATA_DIR=C:\memory-game-demo-data`, mở terminal trong thư mục demo riêng, chạy `java -cp "C:\duong-dan-du-an\out;C:\duong-dan-du-an\lib\*" memorygame.server.ServerMain 12345 --headless`. Ghi đúng PID của tiến trình này bằng `jps -l`; không dùng lệnh tắt toàn bộ Java. Cũng có thể dùng `run-server.bat` / `sh run-server.sh` với biến thư mục dữ liệu này.

Đăng ký hai tài khoản `demo_a`, `demo_b`. Tài khoản mới có 0 xu: trước buổi demo, chơi các ván chuẩn bị theo luật cho đến khi `demo_a` đủ mua vật phẩm đã chọn. Không sửa giá hay cấp xu thủ công trên database thật. Ghi nhận trạng thái ban đầu của hai tài khoản trước bước mua hàng và trước trận chính.

## Trình diễn (khoảng 10–15 phút)

1. A và B đăng nhập trên hai máy. A chọn B → **Kết bạn**; B chấp nhận. Hai bên thấy nhau trong bạn bè.
2. A mở **Thì thầm**, gửi `Demo hai máy – trước restart`; B trả lời. B đăng xuất rồi đăng nhập lại: bạn bè và hai tin nhắn vẫn còn.
3. A mở cửa hàng, ghi giá và số xu, mua một vật phẩm chưa có, trang bị. Xu giảm đúng giá. Thử mua lại nếu giao diện cho phép: không trừ lần nữa. Đăng nhập lại và kiểm tra vật phẩm còn được sở hữu/trang bị. Đối chiếu SQL mua hàng phía dưới.
4. Ghi số xu/rating/games/wins của cả hai **sau mua hàng, trước trận chính**. A tạo phòng riêng 2 người, bàn 4×4; B vào bằng mã. Hai người sẵn sàng, A bắt đầu. Ghi UUID trận từ SQL theo mã phòng.
5. Chơi vài lượt, ghi điểm và các cặp đã ghép. Đến lượt hiện tại, mở đúng **một thẻ** và đợi cả hai client nhận thẻ. Ghi vị trí/hình thẻ, người đang đến lượt; xác nhận snapshot tồn tại trong SQL. Tắt cưỡng bức đúng tiến trình server demo: macOS/Linux `kill -9 "$DEMO_SERVER_PID"`; Windows `taskkill /PID <PID-server-demo> /F`. Giữ MySQL hoạt động.
6. Khởi động lại cùng server, cùng URL database demo, cùng runtime và thư mục làm việc. Với ví dụ shell trên, chạy lại dòng `java ... &` và gán lại PID. Hai client tự kết nối lại; nếu hết lượt thử thì đăng nhập lại. Cả hai bấm **Xác nhận tiếp tục** trong 120 giây. Kiểm tra mã trận không đổi, thẻ/điểm/người đến lượt đúng trạng thái đã lưu; thẻ chưa mở vẫn bị che. Đồng hồ dựa trên snapshot gần nhất, không đòi trùng từng mili giây với ảnh màn hình.
7. Chơi đến hết 8 cặp. Tổng điểm hai người bằng 8. Ghi bảng kết quả, xu thưởng và rating; nếu hòa thì áp dụng kết quả hòa hiện có, không ép có người thắng. Đối chiếu SQL dưới đây: mỗi người đúng một kết quả và một dòng thưởng; snapshot trận đã bị xóa.
8. Đăng xuất, đăng nhập lại cả hai: bạn bè, tin nhắn, vật phẩm, lịch sử hai máy và số dư đúng. Khởi động server lại thêm một lần, truy vấn lại: không tăng xu/rating/games, không thêm dòng thưởng cho cùng UUID.

Để trình diễn thêm các điểm crash khác, tạo **ván mới** cho từng lần: mở cặp sai và tắt trong 2 giây chờ úp; tắt ngay khi thông báo chuyển lượt đến; tắt khi đang tính kết quả. Hai pha đầu có cửa sổ ngắn, cần ghi lại thao tác. Pha đang ghi kết quả khó bắt bằng tay; test `CrashPhasesTest` giữ lỗi SQL trong schema tạm để xác định chính xác SETTLING, không tạo trigger đó trên database đang dùng. Không coi việc tắt sau khi đã hiện kết quả là kiểm tra crash giữa transaction.

## SQL đối chiếu (chỉ đọc dữ liệu)

Chạy trong một phiên MySQL quản trị riêng; kiểm tra `SELECT DATABASE()` phải là `memory_game_demo`. Thay tên tài khoản/mã phòng nếu dùng tên khác. Các biến `@...` dưới đây tồn tại trong cùng phiên MySQL; nếu dùng Adminer với các yêu cầu tách phiên, ghi lại giá trị và nhập lại biến trước truy vấn.

```sql
USE memory_game_demo;
SELECT username, coins, rating, wins, games, equipped_theme, equipped_title
FROM users WHERE username IN ('demo_a','demo_b');
SELECT sender, recipient, status FROM friendships
WHERE sender IN ('demo_a','demo_b') AND recipient IN ('demo_a','demo_b');
SELECT sender, recipient, message_text, sent_at, read_at FROM direct_messages
WHERE sender IN ('demo_a','demo_b') AND recipient IN ('demo_a','demo_b') ORDER BY id;
SELECT username, product_id FROM user_items WHERE username IN ('demo_a','demo_b');
SELECT operation_id, username, amount, reason FROM coin_transactions
WHERE username='demo_a' AND reason='PURCHASE';
```

Trước trận chính, lưu mốc sau mua hàng (giữ cùng phiên SQL tới khi kết thúc):

```sql
SELECT coins,rating,games,wins INTO @a_coins,@a_rating,@a_games,@a_wins FROM users WHERE username='demo_a';
SELECT coins,rating,games,wins INTO @b_coins,@b_rating,@b_games,@b_wins FROM users WHERE username='demo_b';
```

Sau khi bắt đầu, lấy UUID đúng mã phòng từ danh sách, rồi gán `@match` bằng UUID đó; giữ nguyên qua restart:

```sql
SELECT match_id,room_code,status,started_at FROM matches WHERE status='PLAYING' ORDER BY started_at DESC;
SET @match='<UUID-trận-demo>';
SELECT match_id,revision,saved_at FROM match_snapshots WHERE match_id=@match;
```

Sau kết thúc:

```sql
SELECT m.match_id,m.status,r.username,r.score,r.placement,r.winner,
       r.coin_reward,r.rating_delta,r.rating_applied,r.win_credit,t.amount AS journal_reward
FROM matches m JOIN match_results r ON r.match_id=m.match_id
LEFT JOIN coin_transactions t ON t.operation_id=CONCAT('reward:',r.match_id,':',LOWER(r.username))
WHERE m.match_id=@match;
SELECT username,COUNT(*) AS reward_rows,SUM(amount) AS total_reward
FROM coin_transactions WHERE match_id=@match AND reason='MATCH_REWARD' GROUP BY username;
SELECT COUNT(*) AS remaining_snapshots FROM match_snapshots WHERE match_id=@match;
SELECT username,result_text FROM match_results WHERE match_id=@match;
SELECT u.username,
 u.coins - IF(u.username='demo_a',@a_coins,@b_coins) AS coin_change,r.coin_reward,
 u.rating - IF(u.username='demo_a',@a_rating,@b_rating) AS rating_change,r.rating_applied,
 u.games - IF(u.username='demo_a',@a_games,@b_games) AS game_change,
 u.wins - IF(u.username='demo_a',@a_wins,@b_wins) AS win_change,r.win_credit
FROM users u JOIN match_results r ON r.username=u.username WHERE r.match_id=@match;
```

Kỳ vọng: `COMPLETED`, hai kết quả, tổng score 8, mỗi người một reward_rows, journal_reward bằng coin_reward, remaining_snapshots bằng 0; coin_change = coin_reward, rating_change = rating_applied, game_change = 1, win_change = win_credit. Không mua thêm đồ hoặc chơi trận khác giữa mốc đo và truy vấn. Thưởng thắng 50/thua 10; kết quả hòa dùng luật hiện có. Sổ giao dịch không có số dư mở đầu cho dữ liệu cũ, vì vậy không so toàn bộ `SUM(amount)` với số dư tài khoản cũ.

## Biên bản cần điền khi chạy hai máy thật

Ngày/giờ; hệ điều hành/Java của A và B; commit mã nguồn; IP A/cổng; tên database demo; UUID trận; điểm/xu/rating trước–sau; thời điểm kill/restart; kết quả xác nhận tiếp tục; ảnh hai màn hình và kết quả SQL. Đánh dấu từng bước đạt/chưa đạt. Chưa thực hiện buổi demo này thì phải giữ trạng thái **chưa kiểm thử hai máy vật lý**.
