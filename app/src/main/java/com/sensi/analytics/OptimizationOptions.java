package com.sensi.analytics;

/**
 * OptimizationOptions gom toàn bộ lựa chọn bật/tắt và giá trị thanh kéo mà
 * người dùng tự chỉnh ở khối "Tuỳ chỉnh nâng cao" trên giao diện, trước khi
 * bấm "Áp dụng tối ưu". Mỗi cờ tương ứng 1 nhóm lệnh AN TOÀN trong
 * AnalyticsService — người dùng có thể tắt bớt nếu không muốn áp dụng.
 */
public class OptimizationOptions {

    /** Giảm animation scale hệ thống (window/transition/animator) */
    public boolean reduceAnimation = true;

    /** Giới hạn số tiến trình nền tối đa */
    public boolean limitBackgroundProcesses = true;

    /** Ẩn hiển thị điểm chạm + giảm thời gian nhận long-press */
    public boolean fasterTouchResponse = true;

    /** Yêu cầu hệ thống ưu tiên tần số quét cao nhất màn hình hỗ trợ */
    public boolean boostRefreshRate = true;

    /** Giải phóng bộ nhớ đệm hệ thống hiện tại */
    public boolean clearCache = true;

    /** Bật GPU rendering cưỡng bức + tắt hiệu ứng bàn phím ảo */
    public boolean gpuRenderingBoost = true;

    /**
     * Bật chế độ Hiệu năng (Game Mode) của Android cho Free Fire — dùng API
     * Game Mode chính thức của Android 12+ (GameManager), KHÔNG phải hack.
     * Mặc định TẮT vì cần biết đúng package name của game đang cài, và không
     * phải máy/Android nào cũng hỗ trợ.
     */
    public boolean enableAndroidGameMode = false;

    /**
     * Dọn RAM nền (đóng các app đang chạy nền) trước khi vào game.
     * Mặc định TẮT và cần người dùng chủ động bật, vì thao tác này có thể
     * đóng các app khác đang mở (ví dụ app nghe nhạc, chat...).
     */
    public boolean killBackgroundApps = false;

    /**
     * Ghi đè animation scale thủ công từ thanh kéo trên UI (0.05 - 1.0).
     * Giá trị -1 nghĩa là dùng mức tự động theo tier thiết bị (mặc định).
     */
    public float animationScaleOverride = -1f;

    /**
     * Ghi đè giới hạn tiến trình nền thủ công từ thanh kéo trên UI (1 - 8).
     * Giá trị -1 nghĩa là dùng mức tự động theo tier thiết bị (mặc định).
     * KHÔNG bao giờ cho phép giá trị 0 — 0 tiến trình nền có thể khiến hệ
     * thống đóng cả các app cần thiết (bàn phím, dịch vụ nền quan trọng...).
     */
    public int backgroundLimitOverride = -1;

    /** Package name của Free Fire, dùng riêng cho enableAndroidGameMode */
    public static final String FREE_FIRE_PACKAGE = "com.dts.freefireth";
}
