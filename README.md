# Game lật thẻ Online 2–4 người

Ứng dụng desktop Java Swing sử dụng TCP Socket, được xây dựng theo tài liệu
`Mo_ta_game_lat_the_online_2_den_4_nguoi.docx`.

## Chức năng

- Màn hình đăng nhập hai cột tông kem, tím và xanh ngọc, có nút hiện/ẩn mật khẩu.
- Tách riêng màn hình đăng ký và đăng nhập; đăng nhập không còn tự tạo tài khoản, đồng thời chặn đăng nhập trùng phiên.
- Sảnh chờ giao diện Vườn kẹo: nền kem, khung trắng, tím và xanh ngọc, có danh sách online, ghép nhanh, phòng riêng và thanh hồ sơ/âm thanh. Tên người chơi, xu, điểm xếp hạng, số trận thắng và trạng thái cập nhật từ backend.
- Nút **Hồ sơ** trong sảnh: xem thông tin tài khoản, đổi mật khẩu bằng mật khẩu hiện tại, chọn avatar PNG/JPG (dưới 10 MB, tự cắt vuông 128×128), hoặc trở lại mèo mặc định. Avatar được lưu theo tài khoản và hiển thị ở danh sách online/bảng xếp hạng. Migration bổ sung cột `users.avatar` cho MySQL; dữ liệu tài khoản cũ vẫn được giữ.
- Ghép trận tự động cho 2, 3 hoặc 4 người theo đúng kích thước bàn đã chọn; phòng do Server điều phối, không gán chủ phòng cho người chơi.
- Ba chế độ bàn: 4×4 (8 cặp), 6×6 (18 cặp) và 8×8 (32 cặp).
- Tạo phòng, vào phòng bằng mã, gửi/chấp nhận/từ chối lời mời 10 giây.
- Phòng riêng tối đa 4 người, sẵn sàng, chuyển chủ phòng và mời người chơi ra. Màn hình phòng chờ dùng các ô thành viên tông kem, tím và xanh ngọc, hiển thị chủ phòng, bản thân, sẵn sàng/mất kết nối và cấu hình bàn từ backend; chọn ô thành viên để mời ra nếu bạn là chủ phòng.
- Trong phòng ghép ngẫu nhiên, mọi người bấm **Sẵn sàng**; khi đủ người và tất cả đã sẵn sàng, Server đếm ngược 3 giây rồi tự bắt đầu.
- Bàn được tạo theo chế độ đã chọn; server giữ toàn bộ giá trị bí mật và kiểm tra thao tác.
- Lượt 15 giây; đúng được +1 và chơi tiếp, sai hiện 2 giây rồi chuyển lượt.
- Hết lượt tự động, phát hiện 60 giây không hoạt động, bỏ cuộc và giữ chỗ mất kết nối 30 giây.
- Client tự kết nối lại tối đa 10 lần khi mạng chập chờn và khôi phục đúng phòng/trận trong thời hạn giữ chỗ.
- Khi server MySQL khởi động lại, ván đang chơi được khôi phục và tạm dừng; mọi người còn tham gia bấm **Xác nhận tiếp tục** để chơi tiếp với bàn, điểm, lượt và thời gian còn lại đã lưu. Thời hạn chờ là 120 giây kể từ lần phục hồi đầu tiên; người chưa kết nối và xác nhận khi hết hạn được tính bỏ cuộc. Restart lần nữa không gia hạn chờ.
- Sau trận, người chơi có 30 giây chọn Chơi lại; chỉ những người đồng ý mới được giữ trong phòng mới.
- Khi hết trận, hệ thống hiển thị bước tính điểm ngắn rồi xếp kết quả theo số cặp giảm dần; người bằng điểm nhận cùng hạng (`1, 2, 2, 3`).
- Tài khoản, xu, thắng/thua, điểm xếp hạng, vật phẩm và lịch sử được lưu trong MySQL chạy bằng Docker.
- Ví xu được quản lý tại Server: người thắng nhận 50 xu, người thua nhận 10 xu sau mỗi ván.
- Cửa hàng dùng xu mua/trang bị mặt sau thẻ và danh hiệu hồ sơ; vật phẩm chỉ mang tính trang trí, không tạo lợi thế thi đấu.
- Mỗi mặt thẻ có bề mặt giả lập 3D và hiệu ứng riêng: dập chìm Cổ điển, sóng Đại dương, hạt neon hoặc ánh kim Hoàng kim.
- Nhạc nền sảnh: “Call of Silence” — bản nhạc không lời dài khoảng 1 giờ 38 giây (https://www.youtube.com/watch?v=ow_rqR-tmDk), phát toàn bộ từ đầu đến cuối rồi lặp lại; đọc WAV theo luồng với bộ đệm cố định; có nút bật/tắt riêng tại Lobby. Hiệu ứng thao tác, lật thẻ, ghép đúng/sai được tạo trực tiếp trong ứng dụng.

Trong trận, mở tab **Chat phòng** cạnh bàn chơi, nhập tin nhắn rồi nhấn Enter hoặc **Gửi**.
Tin nhắn hiển thị cho tất cả người chơi cùng phòng và trong phần **Chat phòng · Theo dõi**
của phòng đang chọn trên màn hình server. Mỗi tin tối đa 300 ký tự; server giữ 100 tin gần nhất
trong bộ nhớ để khôi phục khi kết nối lại, xóa khi bắt đầu ván mới hoặc phòng bị giải tán.
Cần biên dịch lại và khởi động lại cả server lẫn client sau khi cập nhật giao thức chat.

## Yêu cầu

- JDK 17 trở lên. Không cần Maven, Gradle hoặc JavaFX.
- Docker Desktop dành cho máy chạy Server.

## Chạy trên Windows

1. Mở Docker Desktop.
2. Chạy `setup-database.bat` một lần để tạo MySQL và tải JDBC driver.
3. Cấu hình biến môi trường database theo [hướng dẫn migration](docs/database.md#áp-dụng), rồi chạy `migrate-database.bat` để biên dịch và nâng cấp schema trước khi chạy server.
4. Chạy `run-server.bat` và giữ cửa sổ server mở.
5. Chạy `run-client.bat` từ 2 đến 4 lần.
6. Đăng ký tài khoản mới ở màn Đăng ký, sau đó mỗi cửa sổ đăng nhập bằng một tài khoản khác nhau.
7. Chọn chế độ 4×4, 6×6 hoặc 8×8 trước khi ghép trận hay tạo phòng.

Server mặc định lắng nghe cổng `12345`. Client dùng `localhost` khi chơi trên cùng máy.
Nếu chơi qua mạng LAN, Client nhập địa chỉ IPv4 của máy chạy Server và firewall phải cho phép TCP 12345.

Trong sảnh, chọn **Bảng xếp hạng** để xem top 3, điểm, thắng/trận và tỷ lệ thắng từ backend; tài khoản của bạn được đánh dấu trong bảng.

Trong sảnh, chọn **Lịch sử trận đấu** để xem bảng kết quả và tổng số cặp/xu trong tối đa 50 trận gần nhất được backend lưu. Bản ghi cũ thiếu thông tin hiển thị `—`.

## Màn hình giám sát Server

Sau khi biên dịch lại bằng `compile.bat`, chạy `run-server.bat` để mở cửa sổ **Giám sát Server**.

- Giao diện đồng bộ với client: nền kem, tím và điểm nhấn xanh ngọc; danh sách phòng bên trái, chi tiết người chơi và bàn đấu bên phải.
- Tổng quan số người online, người đang ghép trận, số phòng và số trận đang chơi. Toàn bộ số liệu lấy trực tiếp từ `GameServer.monitorSnapshot()`, không dùng dữ liệu mẫu trong ứng dụng.
- Chọn một phòng để xem người chơi, điểm số, kết nối, trạng thái sẵn sàng/bỏ cuộc, lượt và thời gian còn lại.
- Bàn thẻ 4×4, 6×6 hoặc 8×8 cập nhật mỗi 0,5 giây; thẻ đang úp vẫn được che. Màu thẻ đang lật và màu riêng của từng người ghép được dùng chung với các client (tím, xanh ngọc, cam, xanh dương), theo thứ tự người chơi của từng trận. Rê chuột lên thẻ để xem người ghép.
- Phòng vừa kết thúc còn hiển thị trong thời gian chờ chơi lại; màn hình không lưu danh sách trận đã đóng.
- Cửa sổ chỉ quan sát trên máy chủ. Đóng cửa sổ giám sát không dừng server; dùng `Ctrl+C` tại terminal để dừng.

Để chạy không giao diện (hoặc trên máy không có màn hình):

```sh
java -cp "out;lib/mysql-connector-j-26.7.0.jar" memorygame.server.ServerMain 12345 --headless
```

Trên macOS/Linux, thay dấu `;` trong classpath bằng `:`. Máy không hỗ trợ đồ họa tự động chạy không giao diện.

## Quản lý dữ liệu MySQL

Chạy `open-database-admin.bat`, sau đó đăng nhập Adminer tại `http://localhost:8080`:

- Hệ quản trị: `MySQL`
- Máy chủ: `mysql`
- Tài khoản: `memory_game`
- Mật khẩu: `<MEMORY_GAME_DB_PASSWORD>`
- Cơ sở dữ liệu: `memory_game`

Các đối tượng chính:

- `users`: tài khoản, xếp hạng, thắng, thua, tổng trận, xu và vật phẩm đang trang bị.
- `products`: danh mục vật phẩm của cửa hàng, giá và trạng thái bán.
- `user_items`: các vật phẩm từng tài khoản đã mua, liên kết tới `users` và `products`.
- `matches`: thông tin từng trận, mã mới cho mỗi lần chơi lại.
- `match_snapshots`: trạng thái bí mật của ván đang chơi, chỉ đọc tại server để phục hồi.
- `match_results`: kết quả từng người trong trận, liên kết tới `matches` và `users`.
- `match_history`: giữ dữ liệu lịch sử cũ; lịch sử mới đọc từ `matches` và `match_results`.
- `leaderboard`: view có sẵn cho xếp hạng và tỷ lệ thắng.
- `friendships`: lời mời và quan hệ bạn bè, chống tự kết bạn và trùng cặp.
- `direct_messages`: tin nhắn riêng, thời gian gửi/đã đọc và chỉ mục phân trang.
- `coin_transactions`: sổ thay đổi xu với mã nghiệp vụ duy nhất; không dựng lịch sử giao dịch cũ.
- `private_messages`: nguồn tin nhắn cũ được giữ nguyên sau migration.

Tin nhắn riêng mới được lưu trên server trước khi chuyển tới người nhận. Sau khi đăng nhập, client tự đồng bộ tối đa 100 tin gần nhất mỗi cuộc trò chuyện (không đánh dấu tin lịch sử là tin chưa đọc). MySQL giữ các bản ghi tin nhắn; giới hạn 100 áp dụng cho phần lịch sử đồng bộ ban đầu. Nút **Tin cũ hơn** tải từng trang 50 tin từ database; **Mới nhất** quay về trang gần nhất. Trạng thái đã đọc được ghi khi mở cửa sổ trò chuyện. Cần cập nhật và khởi động lại cả server lẫn client. Lịch sử cũ chỉ có trong file trên máy client vẫn được giữ tại máy đó, không tự tải lên server. Chat trong trận nằm trong snapshot của phòng ở chế độ MySQL. Chế độ `MEMORY_GAME_STORAGE=file` lưu tin riêng trong `data/private-messages.bin`.

Dữ liệu MySQL nằm trong Docker volume `memory-game-mysql-data` và vẫn còn sau khi container dừng.
Server nhập các tài khoản còn thiếu từ `data/state.bin`, giữ nguyên tài khoản đã có trong database. Bạn bè từ `friends.properties` và tin nhắn từ `private-messages.bin` chỉ được nhập khi nguồn thực sự tồn tại; giữ nguyên tệp nguồn và không nhập trùng khi chạy lại.
MySQL của game dùng cổng `3307` trên máy chủ để tránh xung đột với dịch vụ đang dùng cổng `3306`.

Chi tiết nâng cấp, transaction và kiểm thử database: [docs/database.md](docs/database.md).

## Kiểm thử

Chạy `run-tests.bat`. Bộ test tự mở server riêng ở cổng 12347, kiểm tra cả ba kích thước bàn,
kiểm tra trạng thái giám sát (ẩn thẻ úp, cập nhật thẻ lật, bản sao không bị thay đổi),
chơi trọn một ván 4×4, xác nhận tổng điểm và kiểm tra luồng Chơi lại; server thật ở cổng 12345 không bị ảnh hưởng.

## Luật nhanh

- Trong phòng ghép ngẫu nhiên, không có người chơi nào là chủ phòng; Server tự bắt đầu sau khi mọi người sẵn sàng và đếm ngược 3 giây.
- Trong phòng riêng, mọi người bấm **Sẵn sàng**, sau đó chủ phòng bấm **Bắt đầu**; Server đếm ngược 3 giây trước khi vào trận.
- Khi tên bạn xuất hiện ở dòng **Lượt**, hãy chọn lần lượt hai ô đang úp.
- Ghép đúng: hai thẻ giữ nguyên, bạn nhận 1 điểm và tiếp tục lượt mới.
- Ghép sai: hai thẻ nâng lên để người chơi ghi nhớ, xoay úp lại và lượt chuyển sang người kế tiếp.

### Chủ đề nội dung thẻ

Trước khi tìm trận, chọn **Chủ đề** ngay dưới **Kích thước** trong phần **Ghép nhanh**. Lựa chọn này đồng bộ với mục **Chủ đề thẻ** trong màn chơi: Trái cây (mặc định), Động vật, Đồ ăn hoặc Phương tiện. Mỗi chủ đề có 32 hình minh họa riêng, hỗ trợ bàn 4×4, 6×6 và 8×8. Mặt thẻ chỉ hiển thị hình ảnh, không có số hoặc nhãn chữ. Ảnh PNG được đóng gói sẵn, không cần mạng hoặc phông emoji. Có thể đổi chủ đề trong trận; lựa chọn chỉ thay đổi giao diện trên máy của bạn. Backend vẫn quyết định các cặp thẻ, lượt chơi và điểm số.

Khi biên dịch thủ công, sao chép thư mục `src/memorygame/client/images` vào `out/memorygame/client/images`. `compile.bat` đã tự động thực hiện bước này.

### Tạo phòng tùy chỉnh

Nút **Tạo phòng** mở hộp thoại nhập tên phòng (1–40 ký tự), giới hạn 2/3/4 người, chủ đề hình ảnh và độ khó: Dễ 4×4, Trung bình 6×6, Khó 8×8. Chủ phòng bấm **Xác nhận tạo phòng** để gửi cấu hình lên server. Hủy hoặc đóng hộp thoại không tạo phòng.

Server kiểm tra giới hạn khi vào bằng mã hoặc nhận lời mời, và gửi tên, chủ đề, kích thước, giới hạn người cho các thành viên. Chủ đề phòng riêng dùng chung và không đổi trong trận; cấu hình được giữ khi chơi lại hoặc kết nối lại. Vẫn cần ít nhất 2 người và tất cả sẵn sàng để chủ phòng bắt đầu.

Sau cập nhật này, khởi động lại cả **server và client** để server nhận và áp dụng các lựa chọn mới.

Trong phòng chờ, khung **Người chơi online** hiển thị người đang rảnh từ server, loại trừ bản thân và thành viên phòng. Chọn tên rồi bấm **Mời vào phòng**; lời mời có hiệu lực 10 giây. Không thể mời khi phòng đầy, đang đếm ngược, chờ chơi lại hoặc là phòng ghép nhanh.

Người được mời nhận bảng nhỏ hiển thị người mời, tên/mã phòng, số người, chủ đề và độ khó. Có thể chấp nhận hoặc từ chối; bảng đếm ngược và tự đóng sau 10 giây nếu chưa phản hồi. Server cũng từ chối lời mời quá hạn.

### Tương tác trong danh sách người chơi online

- Bấm vào một người chơi khác để mở hai mục **Kết bạn** và **Thì thầm**.
- Nút **+** bên phải mỗi dòng gửi lời mời vào phòng khi cả hai người đang rảnh.
- Kết bạn cần người nhận chấp nhận; lời mời hết hạn sau 2 phút. Ở chế độ MySQL, lời mời và bạn bè được lưu trong `friendships`; chế độ tệp vẫn dùng `friends.properties`. Lời mời còn hạn được gửi lại khi người nhận đăng nhập.
- Thì thầm chỉ gửi đến người nhận và người gửi, tối đa 300 ký tự; lịch sử giữ tối đa 100 tin gần nhất mỗi cuộc trò chuyện trong `.memory-game-online/chats` tại thư mục người dùng trên máy client, tách riêng theo máy chủ và tài khoản. Bấm biểu tượng trò chuyện ở góc danh sách người online để xem những người đã nhắn tin và mở lại cuộc trò chuyện. Tin nhắn mới được lưu trên server và đồng bộ khi đăng nhập trên máy khác; lịch sử cũ chỉ có trên client vẫn giữ tại máy đó.
- Biên dịch và khởi động lại cả server lẫn client để sử dụng các thông điệp kết bạn/thì thầm mới.

Bấm **+ ở sảnh** để mở **Thiết lập trận đấu**, chọn tên phòng, số người, chủ đề và độ khó, rồi bấm **Tạo phòng và gửi lời mời**. Server tạo phòng trước và gửi mã phòng thật; nếu người nhận đã bận hoặc offline thì không tạo phòng. Nút **+ trong phòng chờ** vẫn mời ngay vào phòng hiện tại. Cần cập nhật và khởi động lại cả server lẫn client cho luồng này.

### Kiểm thử hồ sơ cá nhân

Sau khi chạy `sh compile.sh`:

```sh
javac -encoding UTF-8 -cp out -d out test/ProfileTest.java
MEMORY_GAME_STORAGE=file java -Djava.awt.headless=true -cp out memorygame.test.ProfileTest
```

Kiểm thử dùng server cổng 12481 và thư mục tạm riêng: xác thực, mật khẩu sai/ngắn, ảnh lỗi, lưu avatar, đổi mật khẩu, khởi động lại và đặt avatar mặc định.
