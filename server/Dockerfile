FROM python:3.11-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py .
COPY static ./static

# license.db được tạo tự động trong thư mục làm việc khi server khởi động lần đầu.
# Nếu deploy lên nền tảng có volume lưu trữ lâu dài (Render, Fly.io, VPS...), nhớ
# gắn volume vào /app để dữ liệu key không mất khi container khởi động lại.
ENV LICENSE_DB_PATH=/app/license.db

EXPOSE 8000
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000"]
