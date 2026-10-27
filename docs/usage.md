# Hướng dẫn sử dụng

Xem [README](../README.md) để cài đặt, khởi động và đọc luật chơi. Tài liệu này mô tả các thao tác chi tiết theo source hiện tại.

## Tài khoản và sảnh chờ

Đăng ký và đăng nhập là hai bước riêng; đăng nhập không tự tạo tài khoản và server chặn hai phiên cùng tài khoản. Sảnh có danh sách online, ghép nhanh, phòng riêng, hồ sơ và điều khiển âm thanh. Thông tin xu, rating, thắng và trạng thái lấy từ server.

Trong **Hồ sơ**, xem thông tin, đổi mật khẩu bằng mật khẩu hiện tại, chọn avatar PNG/JPG tối đa 10.000.000 byte (ảnh còn được kiểm tra kích thước), hoặc trở lại mèo mặc định. Client cắt vuông và chuyển ảnh về 128×128; avatar lưu theo tài khoản, hiển thị ở danh sách online và bảng xếp hạng. Migration bổ sung cột avatar cho database cũ.

**Bảng xếp hạng** hiển thị top 3, điểm, thắng/trận và tỷ lệ thắng; đánh dấu tài khoản của bạn. **Lịch sử trận đấu** hiển thị tối đa 50 trận gần nhất cùng tổng số cặp/xu của danh sách; trường dữ liệu cũ chưa biết hiển thị `—`.

## Chủ đề và tạo phòng

- Ghép nhanh: chọn số người, kích thước và **Chủ đề** tại sảnh. Hàng đợi tách theo cả ba lựa chọn.
- **Tạo phòng** mở hộp thoại tên phòng (1–40 ký tự), giới hạn 2/3/4 người, chủ đề và độ khó: Dễ 4×4, Trung bình 6×6, Khó 8×8. Xác nhận mới gửi yêu cầu lên server; hủy không tạo phòng.
- Bốn chủ đề Trái cây (mặc định), Động vật, Đồ ăn, Phương tiện đều có 32 hình PNG đóng gói sẵn, dùng cho cả ba kích thước. Mặt thẻ là hình ảnh, không phụ thuộc mạng hay phông emoji.
- Server hiện tại gửi chủ đề chung cho cả ghép nhanh lẫn phòng riêng. Client khóa mục đổi chủ đề trong trận; cấu hình được giữ khi chơi lại hoặc kết nối lại. Code chỉ cho chọn chủ đề cục bộ khi thông điệp server không có chủ đề, không phải luồng bình thường của phiên bản hiện tại.
- Giới hạn số người được kiểm tra khi vào bằng mã hoặc nhận lời mời. Phòng riêng có thể bắt đầu từ 2 người, không cần đầy; mọi người phải online và sẵn sàng. Chủ phòng có thể chuyển quyền hoặc mời người ra ở phòng chờ. Ghép nhanh do server điều phối.

Server giữ giá trị thẻ bí mật và xác thực lượt. Cặp đúng có màu và mã P1–P4 của người ghép; rê chuột xem tên. Thời gian chờ cặp sai trong `GameServer.pickCard` là 1.310 ms trước bước úp/chuyển lượt.

## Mời người chơi

**+ ở sảnh** mở **Thiết lập trận đấu** để chọn tên, số người, chủ đề và độ khó, rồi **Tạo phòng và gửi lời mời**. Server kiểm tra người nhận còn online/rảnh trước khi tạo phòng và gửi mã thật.

Trong phòng chờ, chọn người rảnh ở danh sách online rồi **Mời vào phòng**, hoặc dùng **+** để mời ngay vào phòng hiện tại. Danh sách loại trừ bản thân/thành viên phòng. Không mời khi phòng đầy, đang chơi/đếm ngược, chờ chơi lại hoặc là phòng ghép nhanh.

Người nhận thấy người mời, tên/mã phòng, số người, chủ đề và độ khó; có 10 giây chấp nhận/từ chối. Hết thời gian hộp thoại tự đóng, server cũng từ chối lời mời quá hạn.

## Kết bạn và trò chuyện

Bấm người chơi khác trong danh sách online để chọn **Kết bạn** hoặc **Thì thầm**. Kết bạn cần người nhận đồng ý; lời mời tiếp tục chờ khi người nhận offline hoặc server khởi động lại, đến khi người nhận chấp nhận hoặc từ chối. Bấm chuông ở góc danh sách online để xem và xử lý lời mời. MySQL giữ lời mời/quan hệ trong `friendships`; chế độ file giữ quan hệ trong `friends.properties` và lời mời trong `friend-requests.properties` dưới thư mục dữ liệu server.

**Chat phòng:** trong trận, mở tab cạnh bàn chơi, nhập và nhấn Enter hoặc **Gửi**. Tin tối đa 300 ký tự, không có ký tự điều khiển; người trong phòng và màn hình giám sát đều xem được. Server giữ 100 tin gần nhất, xóa khi bắt đầu ván mới hoặc giải tán phòng; ở chế độ MySQL, chat nằm trong snapshot phục hồi. Không gửi khi trận tạm dừng hoặc người chơi đã rời cuộc chơi.

**Thì thầm:** chỉ gửi tới người nhận online và người gửi, tối đa 300 ký tự. Nút trò chuyện ở danh sách online mở lại các cuộc hội thoại. Server lưu tin trước khi chuyển; đăng nhập đồng bộ tối đa 100 tin gần nhất mỗi cuộc trò chuyện, không tính lịch sử đồng bộ là tin mới chưa đọc. **Tin cũ hơn** tải từng trang 50 tin; **Mới nhất** quay về trang gần nhất. Dấu đã đọc gắn với trang đang hiển thị/tin nhận khi cửa sổ có focus.

Cache client ở `.memory-game-online/chats` dưới thư mục người dùng, tách theo máy chủ/tài khoản và giữ tối đa 100 tin mỗi cuộc trò chuyện. Cache cũ không tự tải lên server. MySQL giữ lịch sử server độc lập với giới hạn cache; chế độ file lưu `private-messages.bin` trong thư mục dữ liệu server.

## Kết nối lại, kết quả và cửa hàng

Client tự thử kết nối lại tối đa 10 lần. Server giữ chỗ người mất kết nối 30 giây; phát hiện không hoạt động 60 giây và hỗ trợ bỏ cuộc. Khi server game khởi động lại và sử dụng trạng thái đã lưu trong MySQL để phục hồi trận, người chơi phải xác nhận tiếp tục trong 120 giây; khởi động lại server game lần nữa không gia hạn. Xem [cơ chế phục hồi](database.md#transaction-và-đồng-bộ).

Sau khi tính điểm, người đồng điểm nhận cùng hạng. Server quản lý ví/thưởng; mua mặt sau thẻ và danh hiệu không tạo lợi thế. Mặt thẻ có chuyển động nâng/lật và hiệu ứng Cổ điển, sóng Đại dương, neon, ánh kim. Nhạc nền dùng đoạn 5 phút, phát theo luồng rồi lặp; có nút bật/tắt riêng. [README audio](../src/memorygame/client/audio/README.md) ghi nguồn và định dạng; hiệu ứng thao tác được tạo trong ứng dụng.

## Giám sát server

Launcher mở **Giám sát Server** nếu máy hỗ trợ đồ họa. Danh sách phòng và chi tiết cập nhật từ `GameServer.monitorSnapshot()`, gồm số online/ghép trận/phòng/trận, người chơi, điểm, sẵn sàng, kết nối/bỏ cuộc, lượt và thời gian còn lại.

Bàn 4×4/6×6/8×8 cập nhật mỗi 0,5 giây, che thẻ úp, dùng chung màu người chơi với client; rê chuột xem người ghép. Phòng kết thúc còn hiển thị trong thời gian chờ chơi lại; màn hình không giữ các trận đã đóng. Đóng cửa sổ giám sát không dừng server; dùng Ctrl+C trong terminal.

Chạy không giao diện:

```sh
# macOS/Linux
sh run-server.sh 12345 --headless
```

```bat
rem Windows CMD
run-server.bat 12345 --headless
```

Máy không có môi trường đồ họa tự chạy server headless. Biến runtime/MySQL vẫn cần được cấu hình như trong README.

## Kiểm thử và tài nguyên khi biên dịch

Windows: `run-tests.bat` biên dịch rồi chạy bộ tổng ở chế độ file, server chính của test dùng cổng 12347. Bộ test kiểm tra ba kích thước bàn, giám sát/che thẻ/bản sao trạng thái, chat, trọn ván 4×4 và chơi lại; không dùng server thật cổng 12345. Một số ca tự mở server riêng khác.

macOS/Linux:

```sh
sh compile.sh
javac -encoding UTF-8 -cp 'out:lib/*' -d out test/*.java
MEMORY_GAME_STORAGE=file java -Djava.awt.headless=true -cp out memorygame.test.TestSuite
```

Kiểm thử hồ sơ riêng:

```sh
sh compile.sh
javac -encoding UTF-8 -cp out -d out test/ProfileTest.java
MEMORY_GAME_STORAGE=file java -Djava.awt.headless=true -cp out memorygame.test.ProfileTest
```

Ca hồ sơ dùng cổng 12481 và thư mục tạm: xác thực, mật khẩu sai/ngắn, ảnh lỗi, đổi mật khẩu/avatar, restart, đặt lại avatar và giữ thống kê. Kiểm thử MySQL và kết quả đã ghi nhận nằm trong [database.md](database.md#kiểm-thử); các lệnh ở đây là hướng dẫn, không phải báo cáo lần chạy mới.

`compile.sh`/`compile.bat` chép WAV và PNG vào `out/memorygame/client/`. Nếu biên dịch thủ công, cần chép cả thư mục `audio/` và `images/` từ source sang vị trí tương ứng trong classpath. Khi cập nhật giao thức, dùng cùng bản source và khởi động lại cả server/client.
