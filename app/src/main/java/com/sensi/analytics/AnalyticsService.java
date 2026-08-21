package com.sensi.analytics;

/**
 * AnalyticsService là lớp điều phối nghiệp vụ:
 *  - Nhận DeviceInfo + Profile đã tính toán + OptimizationOptions do người
 *    dùng tự bật/tắt trên giao diện
 *  - Áp dụng một số CÀI ĐẶT HỆ THỐNG AN TOÀN thông qua ShellExecutor để máy
 *    chạy mượt hơn khi chơi game (không đụng tới file/tiến trình của game)
 *  - Cung cấp thao tác "Phục hồi cài đặt" để đưa các giá trị về mặc định
 *
 * Các lệnh dùng ở đây đều là lệnh `settings put` / `cmd` tiêu chuẩn của
 * Android, tương đương những gì người dùng có thể tự bật trong "Tùy chọn nhà
 * phát triển" hoặc tính năng Game Mode có sẵn của hệ điều hành. Shizuku chỉ
 * giúp thực hiện các lệnh này mà không cần bật thủ công từng cái.
 *
 * LƯU Ý QUAN TRỌNG VỀ GIỚI HẠN PHẠM VI:
 * App KHÔNG và KHÔNG THỂ tạo ra tần số quét (Hz) giả trên phần cứng không hỗ
 * trợ. App cũng KHÔNG thay đổi độ giật súng, độ hồi tâm ngắm hay bất kỳ cơ
 * chế nào của game Free Fire — những thứ đó do code của game quyết định, hệ
 * điều hành không can thiệp được.
 *
 * Lịch sử phiên bản mức độ tối ưu:
 *   v1.1: +20% so với bản gốc
 *   v1.2: +20% nữa (tổng ~36% animation scale)
 *   v1.5 (bản này): animation scale/background limit đã chạm MỨC SÀN AN TOÀN
 *   ở tier Cao cấp/Khá (không thể giảm/siết thêm mà vẫn đảm bảo hệ thống ổn
 *   định) — vì vậy v1.5 tập trung TĂNG TÁC DỤNG theo hướng MỞ RỘNG PHẠM VI
 *   (thêm nhóm tối ưu mới: GPU rendering, Android Game Mode chính thức, dọn
 *   RAM nền) thay vì tiếp tục siết các giá trị cũ xuống mức có thể gây lag/
 *   crash ứng dụng khác.
 */
public class AnalyticsService {

    private final ShellExecutor shellExecutor;

    // Giá trị mặc định gốc của Android, dùng để phục hồi
    private static final float DEFAULT_ANIMATION_SCALE = 1.0f;
    private static final int DEFAULT_BACKGROUND_LIMIT = -1; // -1 = "chuẩn" (standard limit)
    private static final int DEFAULT_LONG_PRESS_TIMEOUT_MS = 400; // mặc định gốc Android
    private static final int DEFAULT_REFRESH_RATE_OVERRIDE = 0;   // 0 = để hệ thống tự quyết định
    private static final int DEFAULT_GAME_MODE = 1; // 1 = STANDARD (GameManager.GAME_MODE_STANDARD)

    /** Giới hạn tiến trình nền tối thiểu cho phép — KHÔNG bao giờ được xuống 0 */
    private static final int MIN_SAFE_BACKGROUND_LIMIT = 1;
    /** Animation scale tối thiểu cho phép — 0 sẽ khiến 1 số hoạt ảnh phụ thuộc thời lượng bị lỗi */
    private static final float MIN_SAFE_ANIMATION_SCALE = 0.15f;

    public AnalyticsService(ShellExecutor shellExecutor) {
        this.shellExecutor = shellExecutor;
    }

    /** Overload tiện dụng: áp dụng theo tier tự động, dùng toàn bộ nhóm tối ưu mặc định */
    public String applyOptimization(ProfileManager.Profile profile) {
        return applyOptimization(profile, new OptimizationOptions());
    }

    /**
     * Áp dụng tối ưu dựa theo profile được đề xuất + các lựa chọn bật/tắt của
     * người dùng (OptimizationOptions). Trả về log chi tiết từng lệnh đã chạy
     * để hiển thị cho người dùng xem (mục "Xem log lệnh" trên giao diện).
     */
    public String applyOptimization(ProfileManager.Profile profile, OptimizationOptions options) {
        StringBuilder log = new StringBuilder();

        float animationScale = resolveAnimationScale(profile, options);
        int backgroundLimit = resolveBackgroundLimit(profile, options);

        if (options.reduceAnimation) {
            log.append("== Giảm animation hệ thống ==\n");
            log.append(shellExecutor.run("settings put global window_animation_scale " + animationScale)).append("\n");
            log.append(shellExecutor.run("settings put global transition_animation_scale " + animationScale)).append("\n");
            log.append(shellExecutor.run("settings put global animator_duration_scale " + animationScale)).append("\n");
        }

        if (options.limitBackgroundProcesses) {
            log.append("== Giới hạn tiến trình nền ==\n");
            log.append(shellExecutor.run("settings put global background_process_limit " + backgroundLimit)).append("\n");
        }

        if (options.clearCache) {
            log.append("== Giải phóng bộ nhớ đệm ==\n");
            log.append(shellExecutor.run("sync && echo 1 > /proc/sys/vm/drop_caches 2>/dev/null || echo 'Bỏ qua drop_caches (cần quyền cao hơn)'")).append("\n");
        }

        if (options.fasterTouchResponse) {
            log.append("== Tối ưu phản hồi chạm (không phải Hz giả lập) ==\n");
            log.append(shellExecutor.run("settings put system show_touches 0")).append("\n");
            log.append(shellExecutor.run("settings put system pointer_location 0")).append("\n");
            // v1.5: 200ms -> 170ms, vẫn trong khoảng an toàn (Android cho phép 130ms-1500ms)
            log.append(shellExecutor.run("settings put secure long_press_timeout 170")).append("\n");
        }

        if (options.boostRefreshRate) {
            log.append("== Ưu tiên tần số quét cao nhất màn hình hỗ trợ ==\n");
            // Chỉ có tác dụng trên máy có màn hình hỗ trợ; máy 60Hz sẽ không đổi gì.
            log.append(shellExecutor.run("settings put system peak_refresh_rate 165")).append("\n");
            log.append(shellExecutor.run("settings put system min_refresh_rate 144")).append("\n");
        }

        if (options.gpuRenderingBoost) {
            log.append("== Tăng tốc GPU rendering ==\n");
            log.append(shellExecutor.run("settings put global force_gpu_rendering 1")).append("\n");
            log.append(shellExecutor.run("settings put global fancy_ime_animations 0")).append("\n");
        }

        if (options.enableAndroidGameMode) {
            log.append("== Bật Game Mode (API chính thức của Android 12+) ==\n");
            // mode 2 = GameManager.GAME_MODE_PERFORMANCE. Lệnh này chỉ có tác dụng
            // nếu máy chạy Android 12+ và hỗ trợ Game Mode; nếu không sẽ báo lỗi
            // vô hại (được bắt bằng || echo) chứ không làm crash app.
            log.append(shellExecutor.run(
                    "cmd game mode set --mode 2 " + OptimizationOptions.FREE_FIRE_PACKAGE
                            + " || echo 'Máy này không hỗ trợ Game Mode qua lệnh cmd (cần Android 12+)'"
            )).append("\n");
        }

        if (options.killBackgroundApps) {
            log.append("== Dọn RAM nền trước khi chơi ==\n");
            // Chỉ đóng các app đang ở nền (không phải app đang hiển thị trước mắt),
            // dùng lệnh chuẩn 'am kill-all' (tương đương nút "Xoá tất cả" trong
            // trình quản lý đa nhiệm) - không đụng tới tiến trình hệ thống.
            log.append(shellExecutor.run("am kill-all || echo 'Không thể dọn RAM nền trên máy này'")).append("\n");
        }

        return log.toString();
    }

    /** Tính animation scale cuối cùng, ưu tiên giá trị override từ thanh kéo nếu có */
    private float resolveAnimationScale(ProfileManager.Profile profile, OptimizationOptions options) {
        if (options.animationScaleOverride >= 0f) {
            return Math.max(MIN_SAFE_ANIMATION_SCALE, options.animationScaleOverride);
        }
        switch (profile.tier) {
            case "Cao cấp":     return 0.25f;
            case "Khá":         return 0.25f;
            case "Trung bình":  return 0.40f;
            default:            return 0.55f; // Thấp
        }
    }

    /** Tính giới hạn tiến trình nền cuối cùng, ưu tiên giá trị override từ thanh kéo nếu có */
    private int resolveBackgroundLimit(ProfileManager.Profile profile, OptimizationOptions options) {
        if (options.backgroundLimitOverride >= 0) {
            return Math.max(MIN_SAFE_BACKGROUND_LIMIT, options.backgroundLimitOverride);
        }
        switch (profile.tier) {
            case "Cao cấp":     return 1; // mức sàn an toàn
            case "Khá":         return 1; // mức sàn an toàn
            case "Trung bình":  return 2;
            default:            return 2; // Thấp
        }
    }

    /** Phục hồi toàn bộ cài đặt về giá trị mặc định gốc của Android */
    public String restoreDefaults() {
        StringBuilder log = new StringBuilder();
        log.append(shellExecutor.run("settings put global window_animation_scale " + DEFAULT_ANIMATION_SCALE)).append("\n");
        log.append(shellExecutor.run("settings put global transition_animation_scale " + DEFAULT_ANIMATION_SCALE)).append("\n");
        log.append(shellExecutor.run("settings put global animator_duration_scale " + DEFAULT_ANIMATION_SCALE)).append("\n");
        log.append(shellExecutor.run("settings put global background_process_limit " + DEFAULT_BACKGROUND_LIMIT)).append("\n");

        // Phục hồi các cài đặt độ mượt/phản hồi chạm về mặc định gốc
        log.append(shellExecutor.run("settings put system show_touches 0")).append("\n");
        log.append(shellExecutor.run("settings put system pointer_location 0")).append("\n");
        log.append(shellExecutor.run("settings put secure long_press_timeout " + DEFAULT_LONG_PRESS_TIMEOUT_MS)).append("\n");
        log.append(shellExecutor.run("settings put system peak_refresh_rate " + DEFAULT_REFRESH_RATE_OVERRIDE)).append("\n");
        log.append(shellExecutor.run("settings put system min_refresh_rate " + DEFAULT_REFRESH_RATE_OVERRIDE)).append("\n");

        // Phục hồi GPU rendering / IME animation về mặc định
        log.append(shellExecutor.run("settings put global force_gpu_rendering 0")).append("\n");
        log.append(shellExecutor.run("settings put global fancy_ime_animations 1")).append("\n");

        // Phục hồi Game Mode về STANDARD nếu trước đó đã bật PERFORMANCE
        log.append(shellExecutor.run(
                "cmd game mode set --mode " + DEFAULT_GAME_MODE + " " + OptimizationOptions.FREE_FIRE_PACKAGE
                        + " || echo 'Bỏ qua phục hồi Game Mode (không hỗ trợ trên máy này)'"
        )).append("\n");

        return log.toString();
    }
}
