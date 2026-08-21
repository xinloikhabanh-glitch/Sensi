# Sensi Analytics 1.0

Ứng dụng Android (Java) phân tích cấu hình thiết bị và đề xuất thông số tối ưu
cho Free Fire, sử dụng Shizuku API để thực thi một số lệnh cài đặt hệ thống an toàn.

## Cách mở project
1. Mở Android Studio → **Open** → chọn thư mục `SensiAnalytics`.
2. Đợi Gradle sync (project cần kết nối mạng để tải các thư viện `dev.rikka.shizuku:api`
   và `dev.rikka.shizuku:provider`).
3. Build & Run trên thiết bị/máy ảo Android 8.0 (API 26) trở lên.

## Yêu cầu để dùng chức năng "Áp dụng tối ưu" / "Phục hồi cài đặt"
Các chức năng này cần quyền do **Shizuku** cấp (không cần root máy):
1. Cài đặt và khởi chạy Shizuku trên thiết bị (theo hướng dẫn tại
   https://shizuku.rikka.app), thường kích hoạt qua ADB bằng máy tính hoặc
   qua Wireless debugging (Android 11+).
2. Mở "Sensi Analytics 1.0" → bấm **Áp dụng tối ưu** → app sẽ hiện hộp thoại
   xin quyền Shizuku → chọn **Cho phép**.
3. Sau khi cấp quyền, app sẽ tự kết nối `UserService` và có thể chạy lệnh.

## Giới hạn phạm vi có chủ đích
- App **không** chỉnh sửa file cấu hình hay bộ nhớ tiến trình của Free Fire
  hay bất kỳ ứng dụng nào khác.
- Các lệnh shell trong `AnalyticsService.java` chỉ thay đổi cài đặt hệ thống
  Android tiêu chuẩn (animation scale, giới hạn tiến trình nền, ẩn overlay
  điểm chạm, thời gian nhận long-press, ưu tiên tần số quét tối đa mà màn
  hình vật lý hỗ trợ...) — tương tự những gì người dùng có thể tự bật trong
  "Tùy chọn nhà phát triển".
- App **không** và **không thể** tạo ra tần số quét (Hz) giả trên phần cứng
  không hỗ trợ — nếu máy chỉ có màn 60Hz thì lệnh ưu tiên 90/120Hz sẽ không
  có tác dụng gì.
- App **không** làm thay đổi độ giật súng, độ hồi tâm ngắm, quỹ đạo đạn hay
  bất kỳ cơ chế nào của game Free Fire — những thứ đó do code phía client/
  server của game quyết định, hệ điều hành không can thiệp được.
- Bộ 3 thông số đề xuất (Tổng quát/Ngắm/DPI) chỉ mang tính **tham khảo**;
  người chơi tự nhập vào phần Cài đặt độ nhạy bên trong game.

## Cấu trúc mã nguồn
| File | Vai trò |
|---|---|
| `MainActivity.java` | Giao diện chính, điều phối luồng UI |
| `DeviceAnalyzer.java` | Lấy Model/RAM/CPU, tính điểm benchmark |
| `ProfileManager.java` | Quy đổi điểm → bộ 3 thông số đề xuất |
| `ShellExecutor.java` | Quản lý quyền + kết nối Shizuku UserService |
| `UserService.java` / `IUserService.aidl` | Tiến trình thực thi lệnh shell với quyền shell |
| `AnalyticsService.java` | Áp dụng / phục hồi cài đặt hệ thống |
| `OptimizationHistory.java` | Lưu tối đa 3 lần lịch sử gần nhất |

## Hệ thống key kích hoạt (v1.5.2 — dùng server riêng)

App yêu cầu nhập key trước khi vào màn hình chính (`LoginActivity`). Việc
**tạo key**, **xoá/thu hồi key**, và **xác thực key** giờ nằm trên 1 server
API riêng (thư mục `server/`), KHÔNG còn tự xử lý offline trong app nữa.

### Vì sao chuyển sang server thay vì xử lý offline trong app?
- **Thu hồi ngay lập tức**: xoá 1 dòng trong database là key ngừng hoạt động
  trên mọi máy đang cài app — offline không làm được việc này.
- **Không bị chỉnh giờ máy "qua mặt"**: server dùng đồng hồ của chính nó để
  tính hạn dùng, không phụ thuộc đồng hồ điện thoại người dùng.
- **Khoá 1 key = 1 thiết bị**: server ghi nhận thiết bị đầu tiên dùng key đó,
  từ chối thiết bị khác dùng chung — offline không kiểm soát được việc này.

### Bước bắt buộc trước khi build app
Mở `app/src/main/java/com/sensi/analytics/ApiClient.java`, sửa dòng:
```java
public static final String BASE_URL = "https://your-server-url.example.com";
```
thành đúng URL server bạn đã deploy (xem hướng dẫn deploy trong `server/README.md`).
Chưa sửa dòng này thì màn hình nhập key sẽ luôn báo "Không kết nối được server".

### Cách tạo / xoá key (chạy trên máy bạn, gọi lên server đã deploy)
```bash
# Tạo 1 key hạn 1 tháng
curl -X POST https://server-cua-ban.com/keys \
  -H "X-Admin-Token: <ADMIN_TOKEN>" -H "Content-Type: application/json" \
  -d '{"tier": "M", "count": 1}'

# Xoá hẳn 1 key
curl -X DELETE https://server-cua-ban.com/keys/SENSI-M-XXXXXXXXXXXX \
  -H "X-Admin-Token: <ADMIN_TOKEN>"
```
Xem đầy đủ toàn bộ API (tạo/liệt kê/xoá/thu hồi/xác thực) trong `server/README.md`.

### Cơ chế offline tạm thời (grace period)
App vẫn hoạt động được tối đa **24 giờ** không có mạng kể từ lần xác thực
thành công gần nhất (tự động thử xác thực lại với server mỗi 5 phút khi có
mạng trở lại). Quá 24 giờ không xác thực lại được, app bắt buộc quay về màn
hình nhập key.
