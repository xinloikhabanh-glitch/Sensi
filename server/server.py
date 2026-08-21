"""
server.py — API quản lý key kích hoạt cho Sensi Analytics.

Chức năng:
  POST   /keys           - Tạo key mới (cần quyền admin)
  GET    /keys            - Liệt kê toàn bộ key (cần quyền admin)
  DELETE /keys/{key}      - Xoá / thu hồi 1 key (cần quyền admin)
  POST   /validate        - Xác thực key (app gọi, KHÔNG cần quyền admin)

Lưu trữ: SQLite (file license.db, tự tạo khi chạy lần đầu) - đủ cho quy mô
vài nghìn key, không cần cài thêm database server rời.

Chạy thử ở máy local:
    pip install -r requirements.txt
    export ADMIN_TOKEN="doi-chuoi-nay-thanh-bi-mat-cua-ban"
    uvicorn server:app --host 0.0.0.0 --port 8000

Cách deploy thật: xem README.md trong thư mục server/.
"""

import os
import sqlite3
import time
import uuid
from contextlib import contextmanager
from typing import Optional

from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

DB_PATH = os.environ.get("LICENSE_DB_PATH", "license.db")

# Token bí mật để gọi các API quản trị (tạo/xoá/liệt kê key). Đặt qua biến môi
# trường ADMIN_TOKEN khi deploy - KHÔNG dùng giá trị mặc định này khi phát hành thật.
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "doi-token-nay-truoc-khi-deploy-that")

PERMANENT_EPOCH = 4070908800  # năm 2099, coi là "vĩnh viễn"

TIER_SECONDS = {
    "D": 1 * 24 * 3600,
    "W": 7 * 24 * 3600,
    "M": 30 * 24 * 3600,
    "Y": 365 * 24 * 3600,
}

app = FastAPI(title="Sensi Analytics License API")

# Trang quản trị web (server/static/admin.html) — vào bằng đường dẫn /admin.
# Trang này chỉ gọi lại chính các API bên dưới bằng JavaScript, không có
# logic xử lý key nào nằm trong HTML - token admin chỉ lưu tạm trong trình
# duyệt (sessionStorage), không gửi cho ai khác ngoài server này.
STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


@app.get("/admin")
def admin_page():
    return FileResponse(os.path.join(STATIC_DIR, "admin.html"))


@contextmanager
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db():
    with get_db() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS keys (
                key TEXT PRIMARY KEY,
                tier TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                revoked INTEGER NOT NULL DEFAULT 0,
                bound_device_id TEXT,
                note TEXT
            )
        """)


init_db()


def require_admin(x_admin_token: Optional[str]):
    if not x_admin_token or x_admin_token != ADMIN_TOKEN:
        raise HTTPException(status_code=401, detail="Thiếu hoặc sai admin token")


def make_key_string(tier: str) -> str:
    return f"SENSI-{tier}-{uuid.uuid4().hex[:12].upper()}"


class CreateKeyRequest(BaseModel):
    tier: str  # D / W / M / Y / P
    custom_days: Optional[float] = None
    note: Optional[str] = None
    count: int = 1


class ValidateRequest(BaseModel):
    key: str
    device_id: Optional[str] = None


@app.post("/keys")
def create_keys(req: CreateKeyRequest, x_admin_token: Optional[str] = Header(None)):
    require_admin(x_admin_token)

    tier = req.tier.upper()
    if tier not in ("D", "W", "M", "Y", "P"):
        raise HTTPException(status_code=400, detail="tier phải là D/W/M/Y/P")
    if req.count < 1 or req.count > 500:
        raise HTTPException(status_code=400, detail="count phải trong khoảng 1-500")

    now = int(time.time())
    if tier == "P":
        expires_at = PERMANENT_EPOCH
    else:
        duration = int(req.custom_days * 86400) if req.custom_days is not None else TIER_SECONDS[tier]
        expires_at = now + duration

    created_keys = []
    with get_db() as conn:
        for _ in range(req.count):
            key_str = make_key_string(tier)
            conn.execute(
                "INSERT INTO keys (key, tier, created_at, expires_at, revoked, note) VALUES (?, ?, ?, ?, 0, ?)",
                (key_str, tier, now, expires_at, req.note),
            )
            created_keys.append({"key": key_str, "tier": tier, "expires_at": expires_at})

    return {"created": created_keys}


@app.get("/keys")
def list_keys(x_admin_token: Optional[str] = Header(None)):
    require_admin(x_admin_token)
    with get_db() as conn:
        rows = conn.execute("SELECT * FROM keys ORDER BY created_at DESC").fetchall()
        return {"keys": [dict(r) for r in rows]}


@app.delete("/keys/{key}")
def delete_key(key: str, x_admin_token: Optional[str] = Header(None)):
    require_admin(x_admin_token)
    with get_db() as conn:
        row = conn.execute("SELECT * FROM keys WHERE key = ?", (key,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Không tìm thấy key")
        conn.execute("DELETE FROM keys WHERE key = ?", (key,))
    return {"deleted": key}


@app.post("/keys/{key}/revoke")
def revoke_key(key: str, x_admin_token: Optional[str] = Header(None)):
    """Thu hồi key mà KHÔNG xoá khỏi lịch sử (khác /keys/{key} DELETE) - dùng khi
    muốn giữ lại log nhưng chặn không cho dùng nữa."""
    require_admin(x_admin_token)
    with get_db() as conn:
        cur = conn.execute("UPDATE keys SET revoked = 1 WHERE key = ?", (key,))
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail="Không tìm thấy key")
    return {"revoked": key}


@app.post("/validate")
def validate_key(req: ValidateRequest):
    with get_db() as conn:
        row = conn.execute("SELECT * FROM keys WHERE key = ?", (req.key.strip().upper(),)).fetchone()

        if not row:
            return {"valid": False, "message": "Key không tồn tại"}

        if row["revoked"]:
            return {"valid": False, "message": "Key đã bị thu hồi"}

        now = int(time.time())
        if now >= row["expires_at"]:
            return {"valid": False, "message": "Key đã hết hạn", "expires_at": row["expires_at"]}

        # Khoá key vào 1 thiết bị duy nhất (chống chia sẻ key cho nhiều máy).
        # Thiết bị đầu tiên gọi /validate thành công sẽ được gán làm chủ key.
        if req.device_id:
            if row["bound_device_id"] is None:
                conn.execute("UPDATE keys SET bound_device_id = ? WHERE key = ?", (req.device_id, req.key.strip().upper()))
            elif row["bound_device_id"] != req.device_id:
                return {"valid": False, "message": "Key đã được kích hoạt trên thiết bị khác"}

        return {
            "valid": True,
            "message": "Key hợp lệ",
            "tier": row["tier"],
            "expires_at": row["expires_at"],
        }


@app.get("/health")
def health():
    return {"status": "ok"}
