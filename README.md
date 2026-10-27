# Memory Game — Lật thẻ online 2–4 người

Game desktop Java Swing chơi qua TCP. Server quản lý trận và lưu tài khoản, vật phẩm, bạn bè, tin nhắn và lịch sử trong MySQL.

## Chức năng chính

- Đăng ký, đăng nhập, hồ sơ và bảng xếp hạng.
- Ghép nhanh hoặc tạo phòng riêng; mời bạn, sẵn sàng và trò chuyện.
- Bàn 4×4, 6×6, 8×8 với chủ đề Trái cây, Động vật, Đồ ăn hoặc Phương tiện.
- Lịch sử trận, cửa hàng vật phẩm trang trí, nhạc nền và hiệu ứng.
- Kết nối lại và phục hồi trận khi server game khởi động lại, sử dụng trạng thái đã lưu trong MySQL.

## Yêu cầu môi trường

- Tất cả máy: JDK 17 trở lên và môi trường desktop để chạy client.
- Máy server: Docker có Compose, MySQL và JDBC driver `lib/mysql-connector-j-26.7.0.jar`.
- Máy chỉ chạy client không cần Docker, JDBC driver hoặc mật khẩu database.

Các lệnh dưới đây chạy trong thư mục gốc dự án. Script tự biên dịch source vào `out/`; dùng cùng phiên bản source trên máy server và client.

## Chạy trên macOS/Linux

### Lần đầu trên máy server

1. Cài/mở Docker và cấu hình `MYSQL_ROOT_PASSWORD` cùng `MEMORY_GAME_DB_PASSWORD` cho Docker Compose. Xem [cấu hình và phân quyền database](docs/database.md#quy-trình-cài-mớinâng-cấp-cho-demo).
2. Tải MySQL Connector/J 26.7.0 vào `lib/mysql-connector-j-26.7.0.jar` (trên Windows, `setup-database.bat` tải driver tự động), rồi khởi động MySQL và Adminer:

   ```sh
   docker compose up -d mysql adminer
   docker compose ps
   ```

   Chờ MySQL báo `healthy`.
3. Trong terminal server, đặt `MEMORY_GAME_DB_URL`, `MEMORY_GAME_DB_USER` và `MEMORY_GAME_DB_PASSWORD` cho tài khoản migration, rồi chạy `sh migrate-database.sh`.
4. Chuyển `MEMORY_GAME_DB_USER` và `MEMORY_GAME_DB_PASSWORD` sang tài khoản runtime đã được cấp quyền, rồi chạy `sh run-server.sh`.
5. Trên mỗi máy chơi, chạy `sh run-client.command`.

Đặt biến môi trường trong đúng terminal trước khi chạy lệnh. Không ghi mật khẩu thật vào tài liệu hoặc kho mã. Cách tải driver, ví dụ cấu hình, cấp quyền và nâng cấp có tại [docs/database.md](docs/database.md).

### Những lần khởi động sau

Mở Docker. Nếu container MySQL đã được tạo nhưng đang dừng, chạy `docker start memory-game-mysql` và chờ healthy bằng `docker inspect --format='{{.State.Health.Status}}' memory-game-mysql`. Đặt lại biến database của tài khoản runtime trong terminal server, chạy `sh run-server.sh`, rồi chạy `sh run-client.command` trên từng máy chơi. Không cần migration mỗi lần khởi động; khi nâng cấp, làm theo [quy trình trong docs/database.md](docs/database.md#quy-trình-cài-mớinâng-cấp-cho-demo).

## Chạy trên Windows

### Lần đầu trên máy server

1. Cài và mở Docker Desktop. Trong CMD tại thư mục dự án, cấu hình `MYSQL_ROOT_PASSWORD` và `MEMORY_GAME_DB_PASSWORD` cho Docker Compose bằng `set TEN_BIEN=gia_tri`.
2. Chạy `setup-database.bat` để tải driver, khởi động MySQL/Adminer và chờ MySQL sẵn sàng.
3. Đặt `MEMORY_GAME_DB_URL`, `MEMORY_GAME_DB_USER` và `MEMORY_GAME_DB_PASSWORD` cho tài khoản migration, rồi chạy `migrate-database.bat`.
4. Đặt lại `MEMORY_GAME_DB_USER` và `MEMORY_GAME_DB_PASSWORD` cho tài khoản runtime đã được cấp quyền, rồi chạy `run-server.bat` trong terminal đó.
5. Trên mỗi máy chơi, chạy `run-client.bat`.

### Những lần khởi động sau

Mở Docker Desktop. Nếu MySQL container đang dừng, chạy `docker start memory-game-mysql` rồi chờ healthy bằng `docker inspect --format="{{.State.Health.Status}}" memory-game-mysql`. Đặt lại biến database runtime trong terminal server và chạy `run-server.bat`; trên mỗi máy chơi chạy `run-client.bat`. Chỉ chạy migration khi nâng cấp cần cập nhật schema.

## Kết nối và luật chơi

- Server mặc định dùng TCP cổng **12345**. Client kết nối `localhost` nếu cùng máy, hoặc địa chỉ IPv4 LAN của máy server; mở cổng này trên firewall server khi chơi qua mạng.
- Chọn số người, kích thước bàn và chủ đề ở sảnh trước khi ghép nhanh. Với phòng riêng, chọn trong hộp **Tạo phòng**. Chủ đề được lưu cho cả trận và không đổi trong khi chơi.
- Ghép nhanh bắt đầu khi đủ đúng số người đã chọn, mọi người đang online và đều sẵn sàng; server tự đếm ngược 3 giây.
- Phòng riêng cần ít nhất 2 người; tất cả người trong phòng, kể cả chủ phòng, phải online và sẵn sàng. Chủ phòng bấm **Bắt đầu**; không cần chờ phòng đầy. Trận bắt đầu sau đếm ngược 3 giây.
- Mỗi lượt có 15 giây. Chọn hai thẻ: ghép đúng được 1 điểm, giữ cặp và đi tiếp; ghép sai được server giữ lật khoảng **1,31 giây** rồi úp và chuyển lượt.
- Bàn 4×4 có 8 cặp, 6×6 có 18 cặp, 8×8 có 32 cặp. Hạng được tính theo số cặp; người đồng điểm cùng hạng. Thưởng là 50 xu cho người thắng và 10 xu cho người còn lại; vật phẩm chỉ trang trí.
- Sau trận có 30 giây để chọn chơi lại. Khi server game khởi động lại và sử dụng trạng thái đã lưu trong MySQL để phục hồi trận, người chơi phải xác nhận tiếp tục trong 120 giây.

## Tài liệu chi tiết

- [Hướng dẫn sử dụng](docs/usage.md): hồ sơ, phòng, mời bạn, chat, cửa hàng và giám sát server.
- [Database và vận hành](docs/database.md): cài đặt, migration, phân quyền, lưu dữ liệu và kết quả kiểm chứng.
- [Tạo và chạy bản phát hành](docs/release.md): đóng gói riêng client/server từ source hiện tại.
- [Kịch bản demo hai máy](docs/demo-two-machines.md) và [ghi nhận chuẩn bị demo](docs/demo-verification-20261027.txt).
- [Nguồn và định dạng nhạc nền](src/memorygame/client/audio/README.md).
