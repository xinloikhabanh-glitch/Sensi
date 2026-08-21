package com.sensi.analytics;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ApiClient gọi server license (xem thư mục server/ trong project) để xác
 * thực key. Dùng HttpURLConnection có sẵn của Android, không cần thêm thư
 * viện ngoài (Retrofit/OkHttp) để giữ project gọn.
 */
public class ApiClient {

    /**
     * ĐỔI GIÁ TRỊ NÀY thành URL server thật sau khi deploy (xem server/README.md).
     * Ví dụ: "https://sensi-license.onrender.com"
     */
    public static final String BASE_URL = "https://lev1zalnazyrics.onrender.com/admin"

    private static final int TIMEOUT_MS = 10_000;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static class ValidateResponse {
        public boolean valid;
        public String message;
        public String tier;
        public long expiresAt;
        /** true nếu lỗi do KHÔNG kết nối được server (khác với "key sai") */
        public boolean networkError;
    }

    public interface ValidateCallback {
        void onResult(ValidateResponse response);
    }

    /** Gọi POST /validate trên luồng nền, trả kết quả qua callback trên luồng chính (UI thread) */
    public static void validateKey(String key, String deviceId, ValidateCallback callback) {
        EXECUTOR.execute(() -> {
            ValidateResponse response = doValidateRequest(key, deviceId);
            MAIN_HANDLER.post(() -> callback.onResult(response));
        });
    }

    private static ValidateResponse doValidateRequest(String key, String deviceId) {
        ValidateResponse response = new ValidateResponse();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + "/validate");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("key", key);
            body.put("device_id", deviceId);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            InputStreamReader reader = new InputStreamReader(
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(reader)) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            response.valid = json.optBoolean("valid", false);
            response.message = json.optString("message", status >= 200 && status < 300 ? "" : "Lỗi server");
            response.tier = json.optString("tier", "");
            response.expiresAt = json.optLong("expires_at", 0);
            response.networkError = false;

        } catch (Exception e) {
            // Bất kỳ lỗi mạng/timeout/parse nào đều coi là networkError, KHÔNG coi là "key sai"
            // để tránh đá người dùng hợp lệ ra ngoài chỉ vì mất mạng tạm thời.
            response.valid = false;
            response.networkError = true;
            response.message = "Không kết nối được server: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return response;
    }
}
