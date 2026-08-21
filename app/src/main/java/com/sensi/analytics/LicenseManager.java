package com.sensi.analytics;

import android.content.Context;
import android.provider.Settings;

/**
 * LicenseManager quản lý trạng thái key ĐÃ ĐƯỢC SERVER XÁC THỰC, lưu cache
 * cục bộ (SharedPreferences) để app vẫn dùng được khi mất mạng tạm thời.
 *
 * Nguồn xác thực THẬT SỰ giờ nằm ở server (xem thư mục server/) - class này
 * chỉ lưu lại kết quả lần xác thực gần nhất và áp dụng "thời gian ân hạn"
 * (GRACE_PERIOD) khi không có mạng để gọi lại server.
 *
 * Lý do đổi từ xác thực offline (HMAC ký sẵn trong app) sang server:
 *  - Server có thể XOÁ/THU HỒI 1 key ngay lập tức, offline không làm được.
 *  - Server dùng đồng hồ của chính nó, người dùng chỉnh giờ máy không "gian lận" được.
 */
public class LicenseManager {

    private static final String PREFS_NAME = "sensi_license_prefs";
    private static final String KEY_LICENSE_STRING = "license_key";
    private static final String KEY_EXPIRY_EPOCH = "license_expiry";
    private static final String KEY_TIER = "license_tier";
    private static final String KEY_LAST_VERIFIED_EPOCH = "license_last_verified";

    /**
     * Thời gian ân hạn tối đa được phép dùng app khi KHÔNG gọi được server
     * (mất mạng, server sập tạm thời...), tính từ lần xác thực thành công
     * gần nhất. Sau mốc này, dù key cục bộ chưa hết hạn vẫn bắt buộc phải
     * xác thực lại với server mới cho vào tiếp - tránh trường hợp key đã bị
     * thu hồi trên server nhưng máy offline mãi để "né" việc bị đá ra.
     */
    private static final long GRACE_PERIOD_SECONDS = 24 * 3600; // 24 giờ

    public static final long PERMANENT_EPOCH = 4070908800L; // năm 2099

    private final SharedPreferencesHolder holder;

    public LicenseManager(Context context) {
        holder = new SharedPreferencesHolder(context);
    }

    /** Wrapper mỏng quanh SharedPreferences để dễ đọc code hơn */
    private static class SharedPreferencesHolder {
        final android.content.SharedPreferences prefs;
        SharedPreferencesHolder(Context context) {
            prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /** ID thiết bị dùng để server khoá 1 key vào 1 máy duy nhất */
    public String getDeviceId(Context context) {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id != null ? id : "unknown-device";
    }

    private static String tierLabel(String tierCode) {
        switch (tierCode) {
            case "D": return "1 Ngày";
            case "W": return "1 Tuần";
            case "M": return "1 Tháng";
            case "Y": return "1 Năm";
            case "P": return "Vĩnh viễn";
            default:  return "Không xác định";
        }
    }

    /** Lưu lại kết quả 1 lần xác thực THÀNH CÔNG từ server */
    public void saveVerifiedLicense(String rawKey, String tier, long expiresAt) {
        long now = System.currentTimeMillis() / 1000L;
        holder.prefs.edit()
                .putString(KEY_LICENSE_STRING, rawKey.trim().toUpperCase(java.util.Locale.ROOT))
                .putLong(KEY_EXPIRY_EPOCH, expiresAt)
                .putString(KEY_TIER, tierLabel(tier))
                .putLong(KEY_LAST_VERIFIED_EPOCH, now)
                .apply();
    }

    /**
     * Kiểm tra hợp lệ dựa trên cache cục bộ + thời gian ân hạn.
     * Đây là kiểm tra NHANH dùng cho vòng lặp mỗi 60 giây trong MainActivity;
     * việc xác thực lại THẬT với server nên được gọi định kỳ riêng
     * (xem MainActivity - gọi ApiClient.validateKey mỗi vài phút).
     */
    public boolean isLicenseValid() {
        long expiry = holder.prefs.getLong(KEY_EXPIRY_EPOCH, 0);
        long lastVerified = holder.prefs.getLong(KEY_LAST_VERIFIED_EPOCH, 0);
        if (expiry <= 0 || lastVerified <= 0) return false;

        long now = System.currentTimeMillis() / 1000L;
        if (now >= expiry) return false; // key tự nó đã hết hạn

        // Quá lâu chưa xác thực lại được với server -> bắt buộc phải xác thực lại
        if (now - lastVerified > GRACE_PERIOD_SECONDS) return false;

        return true;
    }

    public long getSavedExpiryEpoch() {
        return holder.prefs.getLong(KEY_EXPIRY_EPOCH, 0);
    }

    public String getSavedTierLabel() {
        return holder.prefs.getString(KEY_TIER, "Không xác định");
    }

    public String getSavedKey() {
        return holder.prefs.getString(KEY_LICENSE_STRING, "");
    }

    /** Chuỗi hiển thị thời gian còn lại, dạng "còn 2 ngày 3 giờ" */
    public String getRemainingTimeText() {
        long expiry = getSavedExpiryEpoch();
        if (expiry <= 0) return "Chưa kích hoạt";
        if (expiry >= PERMANENT_EPOCH - 86400) {
            return "Vĩnh viễn";
        }
        long remainSec = expiry - (System.currentTimeMillis() / 1000L);
        if (remainSec <= 0) return "Đã hết hạn";

        long days = remainSec / 86400;
        long hours = (remainSec % 86400) / 3600;
        if (days > 0) {
            return "Còn " + days + " ngày " + hours + " giờ";
        }
        long minutes = (remainSec % 3600) / 60;
        if (hours > 0) {
            return "Còn " + hours + " giờ " + minutes + " phút";
        }
        return "Còn " + minutes + " phút";
    }

    /** Xoá key đã lưu (dùng khi hết hạn/bị thu hồi hoặc người dùng chủ động đăng xuất) */
    public void clear() {
        holder.prefs.edit().clear().apply();
    }
}
