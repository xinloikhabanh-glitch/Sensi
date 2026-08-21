# Sensi Analytics — License Key Server

Server API quản lý key kích hoạt: **tạo key**, **xoá/thu hồi key**, và **xác
thực key** (app gọi mỗi khi cần kiểm tra).

## Chạy thử ở máy local

```bash
cd server
pip install -r requirements.txt
export ADMIN_TOKEN="doi-chuoi-nay-thanh-bi-mat-that-su"
uvicorn server:app --host 0.0.0.0 --port 8000
```

## Deploy lên hosting miễn phí (khuyên dùng: Render.com)

1. Đẩy thư mục `server/` này lên 1 repo GitHub riêng (hoặc dùng chung repo,
   Render cho chọn "Root Directory" = `server`).
2. Vào [render.com](https://render.com) → **New** → **Web Service** → chọn
   repo → Render tự nhận diện `Dockerfile`.
3. Thêm biến môi trường `ADMIN_TOKEN` = 1 chuỗi bí mật dài, ngẫu nhiên.
4. Deploy xong sẽ có URL dạng `https://ten-app.onrender.com`.
5. **Quan trọng**: dịch vụ free của Render sẽ "ngủ" sau ~15 phút không có
   request, lần gọi đầu tiên sau đó sẽ chậm (~30s để "đánh thức"). Nếu cần
   luôn sẵn sàng, cân nhắc gói trả phí hoặc dùng Fly.io / VPS riêng.
6. Ổ đĩa của Render free là **tạm thời** (ephemeral) — mỗi lần deploy lại,
   file `license.db` (danh sách key) sẽ **mất hết**. Nếu cần lưu vĩnh viễn,
   dùng gói có Persistent Disk, hoặc đổi sang Postgres (Render có Postgres
   free tier riêng, cần sửa `server.py` để dùng thay vì SQLite).

## Trang quản trị web (thay cho gõ curl)

Sau khi server chạy (local hoặc đã deploy), vào thẳng đường dẫn:
```
https://ten-app.onrender.com/admin
```
(hoặc `http://localhost:8000/admin` nếu chạy local)

Nhập **X-Admin-Token** (giá trị `ADMIN_TOKEN` bạn đã đặt) vào ô đầu trang →
bấm **Đăng nhập**. Từ đó có thể:
- Tạo key mới (chọn loại D/W/M/Y/P, tuỳ chỉnh số ngày, số lượng, ghi chú)
- Xem danh sách toàn bộ key kèm trạng thái (còn hạn / hết hạn / đã thu hồi)
- **Xoá hẳn** hoặc **Thu hồi** từng key trực tiếp bằng nút bấm

Token chỉ được lưu tạm trong trình duyệt của bạn (sessionStorage) — mất khi
đóng tab, không gửi cho ai ngoài chính server này. **Không chia sẻ link
`/admin` kèm token cho người khác** — ai có token đều tạo/xoá key được.

## Các API (dùng trực tiếp qua curl nếu không muốn dùng trang web)

Tất cả API quản trị (tạo/xoá/liệt kê) yêu cầu header:
```
X-Admin-Token: <giá trị ADMIN_TOKEN bạn đã đặt>
```

### Tạo key
```bash
curl -X POST https://ten-app.onrender.com/keys \
  -H "X-Admin-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"tier": "M", "count": 1}'
```
Tier: `D` (Ngày) / `W` (Tuần) / `M` (Tháng) / `Y` (Năm) / `P` (Vĩnh viễn).
Có thể thêm `"custom_days": 3.5` để tự đặt số ngày hết hạn, hoặc
`"count": 10` để tạo nhiều key cùng lúc.

Kết quả trả về:
```json
{"created": [{"key": "SENSI-M-A1B2C3D4E5F6", "tier": "M", "expires_at": 1755000000}]}
```

### Liệt kê toàn bộ key
```bash
curl https://ten-app.onrender.com/keys -H "X-Admin-Token: <token>"
```

### Xoá hẳn 1 key (mất khỏi lịch sử)
```bash
curl -X DELETE https://ten-app.onrender.com/keys/SENSI-M-A1B2C3D4E5F6 \
  -H "X-Admin-Token: <token>"
```

### Thu hồi 1 key (vẫn giữ lại trong danh sách, chỉ chặn không cho dùng)
```bash
curl -X POST https://ten-app.onrender.com/keys/SENSI-M-A1B2C3D4E5F6/revoke \
  -H "X-Admin-Token: <token>"
```

### Xác thực key (đây là API app Android gọi, KHÔNG cần token admin)
```bash
curl -X POST https://ten-app.onrender.com/validate \
  -H "Content-Type: application/json" \
  -d '{"key": "SENSI-M-A1B2C3D4E5F6", "device_id": "abc123"}'
```

## Cơ chế khoá 1 thiết bị / key

Thiết bị **đầu tiên** gọi `/validate` thành công với 1 key sẽ được ghi nhận
làm chủ sở hữu (`bound_device_id`). Nếu thiết bị khác dùng lại đúng key đó,
server từ chối — chống việc 1 key bị chia sẻ cho nhiều người dùng chung.
Muốn "mở khoá" cho phép đổi thiết bị, xoá key cũ và tạo key mới, hoặc tự
thêm 1 API `reset-device` (chưa có sẵn, có thể yêu cầu bổ sung).
