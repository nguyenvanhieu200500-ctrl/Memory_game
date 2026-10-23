# Changelog

## 2026-10-12

### Logic

- Kết quả trận được Server sắp theo số cặp giảm dần và gán thứ hạng dày; người bằng điểm nhận cùng hạng theo dạng `1, 2, 2, 3`.
- Bổ sung kiểm thử thứ tự kết quả, đồng hạng, phần thưởng xu và luồng chơi lại sau một ván hoàn chỉnh.
- Bổ sung kiểm thử render theo hai thời điểm để xác nhận cả 6 vật phẩm cửa hàng đều có hiệu ứng động thực tế.

### Giao diện

- Thêm bước xử lý 1,7 giây “Đang tính điểm các cặp đấu” trước khi mở kết quả, kèm tiến trình xếp hạng và cập nhật phần thưởng.
- Thiết kế lại kết quả thành từng dòng riêng, hiển thị rõ hạng, người chơi, số cặp, trạng thái, thay đổi điểm xếp hạng và xu nhận được.
- Thu gọn chiều cao từng dòng kết quả, dùng nhãn hạng nhỏ và cố định kích thước; danh sách bám phía trên, không còn bị kéo giãn theo chiều cao hộp thoại.
- Hoàn thiện hiệu ứng toàn bộ vật phẩm cửa hàng: Cổ điển có ánh dập chìm, Tím neon có lõi sáng/hạt chuyển động, Hoàng kim có kim tuyến; hai danh hiệu có preview hào quang và tia sáng riêng, danh mục dùng biểu tượng màu chuyển động nhẹ.

## 2026-10-09

### Logic

- Chuyển lưu trữ production từ Java Serialization sang MySQL 8.4 chạy bằng Docker; thêm bảng tài khoản/thắng-thua, vật phẩm, lịch sử và view bảng xếp hạng.
- Tự động nhập dữ liệu `data/state.bin` vào MySQL khi database mới còn trống; bộ kiểm thử vẫn dùng vùng tệp độc lập.
- Thêm Adminer tại cổng 8080 và Docker volume bền vững để xem, lọc, sửa và sao lưu dữ liệu thuận tiện.
- Tách nghiệp vụ `REGISTER` và `LOGIN`: đăng ký kiểm tra tên trùng/mật khẩu tối thiểu 6 ký tự; đăng nhập chỉ chấp nhận tài khoản đã tồn tại.
- Thêm ví xu lưu bền vững phía Server: thắng nhận 50 xu, thua nhận 10 xu; kết quả trận và lịch sử ghi rõ phần thưởng.
- Thêm cửa hàng do Server xác thực với giao dịch mua/trang bị, kiểm tra số dư và quyền sở hữu để tránh gian lận từ Client.
- Rút thời gian úp lại cặp sai xuống 0,8 giây và đồng bộ chuyển lượt sau hiệu ứng.
- Phòng riêng cũng đếm ngược đồng bộ 3 giây sau khi chủ phòng bấm Bắt đầu; thao tác hủy sẵn sàng, rời phòng hoặc mất kết nối sẽ hủy lần bắt đầu đó.
- Tách rõ hai loại phòng: ghép ngẫu nhiên do Server sở hữu và phòng riêng do người tạo làm chủ.
- Phòng ghép ngẫu nhiên chờ từng người xác nhận sẵn sàng, sau đó Server đếm ngược đủ 3 giây mới bắt đầu; hủy sẵn sàng hoặc mất kết nối sẽ hủy đếm ngược.
- Không cho tham gia bằng mã, gửi lời mời, kick hoặc chuyển chủ trong phòng ghép ngẫu nhiên; phòng riêng giữ nguyên luồng chủ phòng bấm Bắt đầu.
- Hoàn thiện phiên Chơi lại 30 giây: chỉ giữ người đồng ý, xóa trạng thái sẵn sàng và đóng phòng nếu dưới 2 người.
- Ngăn tham gia, mời hoặc kick trong giai đoạn đang chờ quyết định chơi lại.
- Client tự kết nối lại tối đa 10 lần và loại bỏ callback mất kết nối từ socket cũ.
- Giữ cơ chế khôi phục phòng/trận trong 30 giây sau khi mất mạng.
- Bổ sung kiểm thử ghép đúng kích thước bàn, sai lượt, chọn trùng thẻ, chơi trọn ván và tổng điểm.

### Giao diện

- Mở rộng bảng màu với tím điện và cam san hô: áp dụng có chọn lọc cho tiêu đề, hành động chính, đồng hồ, tiến trình và viền bàn; vẫn giữ nền navy để bảo đảm độ tương phản.
- Nâng cấp bàn chơi với nền arena có chiều sâu, lưới ánh sáng nhẹ, HUD lượt/thời gian tách thành một khối rõ ràng và phản hồi hover/nhấn trên thẻ.
- Tất cả mặt thẻ dùng đổ bóng, chuyển sắc và cạnh sáng/tối để tạo chiều sâu 3D; Cổ điển có họa tiết dập chìm, Tím neon có viền/hạt sáng và Hoàng kim có vân cùng dải ánh kim chuyển động.
- Cửa hàng có thẻ xem trước kích thước lớn và chạy hiệu ứng trực tiếp trước khi mua/trang bị; lựa chọn hiện tại được giữ lại sau giao dịch.
- Thêm nhạc nền riêng cho Lobby và hiệu ứng âm thanh cho nút bấm, lật thẻ, ghép đúng, ghép sai; nhạc tự dừng khi vào phòng/trận và có hai công tắc Nhạc/SFX.
- Tăng kích thước chấm màu, tên người chơi và khoảng cách dòng trong bảng điểm để dễ nhận diện khi có nhiều người chơi.
- Thay bảng “Quy trình bắt đầu” trong phòng chờ bằng “Thông tin trận đấu”, hiển thị thời gian lượt, cách tính điểm, màu sở hữu cặp thẻ và thưởng xu.
- Hoàn thiện hiệu ứng thử nghiệm cho Mặt thẻ Đại dương: sóng nước chạy liên tục, dải sáng lướt và gợn nước khi rê chuột; chỉ áp dụng cho thẻ đang úp.
- Thay nhãn trạng thái “Đã kết nối” ở góc phải Lobby bằng nút Đăng xuất; chỉ cho đăng xuất an toàn khi người chơi đang rảnh.
- Cặp thẻ ghép đúng dùng toàn bộ màu của người tìm được thay vì chỉ tô vòng viền; chữ tự đổi sáng/tối để bảo đảm tương phản trên nền vàng và các màu khác.
- Tách thành hai màn hình Đăng nhập và Đăng ký với trạng thái/kiểm tra dữ liệu riêng.
- Thêm ví xu ở góc trên bên phải Lobby và màn Cửa hàng gồm danh mục, mô tả, giá, trạng thái sở hữu/trang bị.
- Thêm hoạt ảnh hai thẻ ghép sai cùng nâng khỏi mặt bàn trong 0,35 giây, giữ mặt sai thêm 0,35 giây để ghi nhớ, sau đó xoay theo trục dọc, đổi sang mặt `?` và hạ xuống; tốc độ phần xoay–úp được giữ nguyên.
- Chuẩn hóa lại toàn bộ design system: nền navy sâu, bề mặt phân cấp, viền trung tính, màu chữ phụ và màu trạng thái nhất quán.
- Thu gọn mật độ hiển thị ở Login, Lobby, phòng chờ và màn chơi; giảm tiêu đề/khối điều khiển quá lớn và căn lại khoảng cách.
- Banner trạng thái dùng nền dịu cùng vạch màu bên trái thay cho các dải màu gắt toàn chiều rộng.
- Dialog được thu gọn, nút hành động căn phải, phần mô tả không còn giống ô nhập liệu, nút đóng tối giản và có thể kéo cửa sổ bằng thanh tiêu đề.
- Đồng bộ màu tooltip, thanh cuộn, ô nhập và danh sách với giao diện tối của ứng dụng.
- Thay toàn bộ hộp thoại mặc định của Java bằng dialog riêng đồng bộ giao diện tối, góc vuông và nút hành động nhất quán.
- Thiết kế lại màn nhập mã phòng với ô mã nổi bật, tự chuyển chữ hoa và kiểm tra đúng 6 ký tự trước khi gửi.
- Đồng bộ giao diện cho thông báo lỗi, lời mời, xác nhận bỏ cuộc, kết quả trận, bảng xếp hạng và lịch sử.
- Chuyển ngôn ngữ thiết kế sang góc vuông: khung nội dung, nút thao tác và thẻ bài dùng đường thẳng, viền mảnh đồng nhất thay cho bo tròn lớn.
- Hiển thị rõ nhãn “Server điều phối”, ẩn các thao tác chỉ dành cho chủ phòng trong phòng ghép ngẫu nhiên và đồng bộ đồng hồ đếm ngược trên mọi máy.
- Tăng độ tương phản cho danh sách chọn 2/3/4 người và 4×4/6×6/8×8, kể cả khi điều khiển đang bị khóa theo trạng thái.
- Làm mới màn hình đăng nhập với nền chuyển màu và khung bo góc.
- Nút, thẻ và danh sách người chơi có trạng thái màu, hover và khoảng cách rõ ràng hơn.
- Thêm nhãn trạng thái kết nối, thanh thời gian trực quan và màu riêng cho từng loại thẻ.
- Phòng hiển thị người đã chọn chơi lại cùng đồng hồ quyết định sau trận.
- Mỗi người chơi có một màu riêng; cặp ghép đúng giữ vòng màu của người tìm được trên mọi client và sau khi kết nối lại.
- Tái cấu trúc Lobby theo luồng nghiệp vụ: danh sách online bên trái; Ghép nhanh, Phòng riêng và Thành tích ở cột thao tác bên phải.
- Các nút tự ẩn/khóa theo trạng thái IDLE, QUEUE, ROOM và PLAYING; Hủy tìm trận chỉ xuất hiện khi đang chờ.
- Phòng chờ có sao chép mã, hướng dẫn từng bước và banner giải thích chính xác điều kiện chưa thể bắt đầu.
- Màn chơi tách bàn, thông tin trận, bảng điểm màu và hướng dẫn thành các vùng rõ ràng.

### Phân phối

- Thêm `run-tests.bat` để chạy toàn bộ integration test trên server riêng.
- Thêm `dist/memory-game-client.zip` để chép trực tiếp sang máy người chơi.
