package com.sensi.analytics;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    // ==== View: khối chính ====
    private CardView cardDeviceInfo;
    private CardView cardResult;
    private TextView tvDeviceInfo;
    private TextView tvResult;
    private TextView tvHistory;
    private TextView tvLog;
    private TextView tvLicenseStatus;
    private ProgressBar progressBar;
    private Button btnAnalyze;
    private Button btnApply;
    private Button btnRestore;
    private Button btnToggleLog;
    private Button btnLogout;

    // ==== View: Tuỳ chỉnh nâng cao ====
    private SwitchMaterial swAnimation, swBackgroundLimit, swTouchResponse,
            swRefreshRate, swClearCache, swGpuBoost, swGameMode, swKillBackground;
    private SeekBar seekAnimation, seekBackgroundLimit;
    private TextView tvAnimationValue, tvBackgroundLimitValue;

    private boolean logVisible = false;

    // ==== Logic ====
    private DeviceAnalyzer deviceAnalyzer;
    private ProfileManager profileManager;
    private OptimizationHistory history;
    private ShellExecutor shellExecutor;
    private AnalyticsService analyticsService;
    private LicenseManager licenseManager;

    // Vòng lặp kiểm tra key định kỳ trong lúc app đang mở - tự kick ra
    // LoginActivity ngay khi key hết hạn/bị thu hồi, không cần đợi người dùng thoát app.
    // Kiểm tra CỤC BỘ (nhanh) mỗi 60s để phản hồi UI mượt; xác thực THẬT với
    // SERVER mỗi 5 phút để bắt kịp trường hợp admin vừa xoá/thu hồi key.
    private final Handler licenseCheckHandler = new Handler(Looper.getMainLooper());
    private static final long LOCAL_CHECK_INTERVAL_MS = 60_000;       // 60 giây
    private static final long SERVER_REVALIDATE_INTERVAL_MS = 300_000; // 5 phút
    private long lastServerRevalidateAtMs = 0L;

    private final Runnable licenseCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!licenseManager.isLicenseValid()) {
                kickToLogin();
                return;
            }
            tvLicenseStatus.setText(licenseManager.getRemainingTimeText());

            long nowMs = System.currentTimeMillis();
            if (nowMs - lastServerRevalidateAtMs >= SERVER_REVALIDATE_INTERVAL_MS) {
                lastServerRevalidateAtMs = nowMs;
                revalidateWithServer();
            }

            licenseCheckHandler.postDelayed(this, LOCAL_CHECK_INTERVAL_MS);
        }
    };

    /** Gọi lại server để xác thực key hiện tại - bắt các trường hợp bị xoá/thu hồi từ xa */
    private void revalidateWithServer() {
        String key = licenseManager.getSavedKey();
        if (key.isEmpty()) return;

        String deviceId = licenseManager.getDeviceId(this);
        ApiClient.validateKey(key, deviceId, response -> {
            if (response.networkError) {
                // Mất mạng tạm thời - KHÔNG kick, cứ dùng cache cục bộ (có giới hạn
                // "thời gian ân hạn" riêng trong LicenseManager.isLicenseValid())
                return;
            }
            if (!response.valid) {
                Toast.makeText(this, response.message, Toast.LENGTH_LONG).show();
                licenseManager.clear();
                kickToLogin();
                return;
            }
            // Vẫn hợp lệ - cập nhật lại cache (phòng trường hợp admin gia hạn thêm)
            licenseManager.saveVerifiedLicense(key, response.tier, response.expiresAt);
        });
    }

    // Lưu kết quả phân tích gần nhất để dùng khi bấm "Áp dụng tối ưu"
    private DeviceAnalyzer.DeviceInfo lastDeviceInfo;
    private ProfileManager.Profile lastProfile;

    // Lắng nghe kết quả xin quyền Shizuku
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode == ShellExecutor.REQUEST_CODE) {
                    if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Đã cấp quyền Shizuku", Toast.LENGTH_SHORT).show();
                        shellExecutor.bindService();
                    } else {
                        Toast.makeText(this, "Bạn cần cấp quyền Shizuku để áp dụng tối ưu", Toast.LENGTH_LONG).show();
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FIX ĐỊNH DẠNG QUYỀN TRUY CẬP: kiểm tra key TRƯỚC khi vẽ giao diện.
        // Nếu không còn hạn -> đá thẳng về LoginActivity, không cho vào MainActivity
        // dù người dùng có cố mở lại app bằng cách nào (deep link, recent apps...).
        licenseManager = new LicenseManager(this);
        if (!licenseManager.isLicenseValid()) {
            kickToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        bindViews();
        setupLogic();
        setupListeners();

        tvLicenseStatus.setText(licenseManager.getRemainingTimeText());
        renderHistory();
    }

    /** Đưa người dùng về màn hình nhập key, xoá sạch key cũ đã hết hạn (nếu có) */
    private void kickToLogin() {
        if (licenseManager.getSavedExpiryEpoch() > 0 && !licenseManager.isLicenseValid()) {
            Toast.makeText(this, getString(R.string.toast_key_expired), Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void bindViews() {
        cardDeviceInfo = findViewById(R.id.cardDeviceInfo);
        cardResult = findViewById(R.id.cardResult);
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        tvResult = findViewById(R.id.tvResult);
        tvHistory = findViewById(R.id.tvHistory);
        tvLog = findViewById(R.id.tvLog);
        progressBar = findViewById(R.id.progressBar);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnApply = findViewById(R.id.btnApply);
        btnRestore = findViewById(R.id.btnRestore);
        btnToggleLog = findViewById(R.id.btnToggleLog);
        btnLogout = findViewById(R.id.btnLogout);
        tvLicenseStatus = findViewById(R.id.tvLicenseStatus);

        swAnimation = findViewById(R.id.swAnimation);
        swBackgroundLimit = findViewById(R.id.swBackgroundLimit);
        swTouchResponse = findViewById(R.id.swTouchResponse);
        swRefreshRate = findViewById(R.id.swRefreshRate);
        swClearCache = findViewById(R.id.swClearCache);
        swGpuBoost = findViewById(R.id.swGpuBoost);
        swGameMode = findViewById(R.id.swGameMode);
        swKillBackground = findViewById(R.id.swKillBackground);
        seekAnimation = findViewById(R.id.seekAnimation);
        seekBackgroundLimit = findViewById(R.id.seekBackgroundLimit);
        tvAnimationValue = findViewById(R.id.tvAnimationValue);
        tvBackgroundLimitValue = findViewById(R.id.tvBackgroundLimitValue);
    }

    private void setupLogic() {
        deviceAnalyzer = new DeviceAnalyzer(this);
        profileManager = new ProfileManager();
        history = new OptimizationHistory(this);

        shellExecutor = new ShellExecutor(this, new ShellExecutor.Callback() {
            @Override
            public void onServiceReady() {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Đã sẵn sàng thực thi lệnh tối ưu", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onPermissionDenied() {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Quyền Shizuku bị từ chối", Toast.LENGTH_SHORT).show());
            }
        });
        analyticsService = new AnalyticsService(shellExecutor);
        Shizuku.addRequestPermissionResultListener(permissionListener);
    }

    private void setupListeners() {
        btnAnalyze.setOnClickListener(v -> { UiAnimUtils.pulse(v); runAnalysis(); });
        btnApply.setOnClickListener(v -> { UiAnimUtils.pulse(v); applyOptimization(); });
        btnRestore.setOnClickListener(v -> { UiAnimUtils.pulse(v); restoreDefaults(); });
        btnToggleLog.setOnClickListener(v -> toggleLog());
        btnLogout.setOnClickListener(v -> {
            licenseManager.clear();
            kickToLogin();
        });

        // Thanh kéo animation: chỉ cho kéo khi công tắc tương ứng đang bật
        seekAnimation.setEnabled(swAnimation.isChecked());
        seekBackgroundLimit.setEnabled(swBackgroundLimit.isChecked());

        swAnimation.setOnCheckedChangeListener((buttonView, isChecked) -> seekAnimation.setEnabled(isChecked));
        swBackgroundLimit.setOnCheckedChangeListener((buttonView, isChecked) -> seekBackgroundLimit.setEnabled(isChecked));

        // progress 0-95 -> animation scale 0.05 - 1.00 (progress=0 nghĩa là "dùng mức tự động")
        seekAnimation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 0) {
                    tvAnimationValue.setText("Tự động theo thiết bị");
                } else {
                    float scale = 0.05f + (progress / 100f);
                    tvAnimationValue.setText(String.format(Locale.getDefault(), "Animation scale: %.2f", scale));
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        // progress 0-7 -> giới hạn tiến trình nền 1-8 (progress=0 nghĩa là "dùng mức tự động")
        seekBackgroundLimit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 0) {
                    tvBackgroundLimitValue.setText("Tự động theo thiết bị");
                } else {
                    int limit = progress + 1;
                    tvBackgroundLimitValue.setText("Giới hạn: " + limit + " tiến trình nền");
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    /** Nút "Phân tích thiết bị": hiển thị loading rồi tính toán kết quả */
    private void runAnalysis() {
        UiAnimUtils.fadeVisibility(progressBar, true);
        btnAnalyze.setEnabled(false);
        tvResult.setText("");

        // Giả lập thời gian phân tích để người dùng thấy rõ thanh loading
        // (bản thân phép tính chỉ mất vài mili-giây)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            lastDeviceInfo = deviceAnalyzer.analyze();
            lastProfile = profileManager.getRecommendedProfile(lastDeviceInfo.score);

            tvDeviceInfo.setText(lastDeviceInfo.toString());
            tvResult.setText("Đề xuất (Tổng quát / Ngắm / DPI):\n" + lastProfile.toString());

            // Hiệu ứng trượt lên + mờ dần cho 2 card, tạo cảm giác kết quả "xuất hiện"
            UiAnimUtils.slideUpFadeIn(cardDeviceInfo);
            UiAnimUtils.slideUpFadeIn(cardResult);

            UiAnimUtils.fadeVisibility(progressBar, false);
            btnAnalyze.setEnabled(true);
            btnApply.setEnabled(true);
        }, 1200);
    }

    /** Gom toàn bộ lựa chọn công tắc + thanh kéo hiện tại thành 1 OptimizationOptions */
    private OptimizationOptions collectOptions() {
        OptimizationOptions options = new OptimizationOptions();
        options.reduceAnimation = swAnimation.isChecked();
        options.limitBackgroundProcesses = swBackgroundLimit.isChecked();
        options.fasterTouchResponse = swTouchResponse.isChecked();
        options.boostRefreshRate = swRefreshRate.isChecked();
        options.clearCache = swClearCache.isChecked();
        options.gpuRenderingBoost = swGpuBoost.isChecked();
        options.enableAndroidGameMode = swGameMode.isChecked();
        options.killBackgroundApps = swKillBackground.isChecked();

        // progress = 0 nghĩa là dùng mức tự động -> giữ nguyên giá trị mặc định (-1)
        if (seekAnimation.getProgress() > 0) {
            options.animationScaleOverride = 0.05f + (seekAnimation.getProgress() / 100f);
        }
        if (seekBackgroundLimit.getProgress() > 0) {
            options.backgroundLimitOverride = seekBackgroundLimit.getProgress() + 1;
        }
        return options;
    }

    /** Nút "Áp dụng tối ưu": xin quyền Shizuku (nếu chưa có) rồi chạy lệnh */
    private void applyOptimization() {
        if (lastProfile == null) {
            Toast.makeText(this, "Hãy phân tích thiết bị trước", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!shellExecutor.isShizukuAvailable()) {
            Toast.makeText(this, "Shizuku chưa chạy. Vui lòng khởi động Shizuku trước.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!shellExecutor.hasPermission()) {
            shellExecutor.requestPermission();
            return;
        }

        if (!shellExecutor.isServiceReady()) {
            shellExecutor.bindService();
            Toast.makeText(this, "Đang kết nối dịch vụ, hãy bấm Áp dụng lại sau 1-2 giây", Toast.LENGTH_SHORT).show();
            return;
        }

        OptimizationOptions options = collectOptions();

        UiAnimUtils.fadeVisibility(progressBar, true);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String log = analyticsService.applyOptimization(lastProfile, options);
            tvLog.setText(log);

            history.addEntry(lastDeviceInfo.score, lastProfile);
            renderHistory();

            UiAnimUtils.fadeVisibility(progressBar, false);
            Toast.makeText(this, getString(R.string.note_apply_disclaimer), Toast.LENGTH_LONG).show();
        }, 800);
    }

    /** Nút "Phục hồi cài đặt": đưa các thông số về mặc định gốc */
    private void restoreDefaults() {
        if (!shellExecutor.isShizukuAvailable()) {
            Toast.makeText(this, "Shizuku chưa chạy. Vui lòng khởi động Shizuku trước.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!shellExecutor.isServiceReady()) {
            Toast.makeText(this, "Cần cấp quyền Shizuku trước khi phục hồi", Toast.LENGTH_SHORT).show();
            return;
        }
        UiAnimUtils.fadeVisibility(progressBar, true);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String log = analyticsService.restoreDefaults();
            tvLog.setText(log);
            UiAnimUtils.fadeVisibility(progressBar, false);
            Toast.makeText(this, "Đã phục hồi cài đặt mặc định", Toast.LENGTH_SHORT).show();
        }, 500);
    }

    /** Hiện/ẩn khối log lệnh đã chạy */
    private void toggleLog() {
        logVisible = !logVisible;
        if (logVisible) {
            UiAnimUtils.slideUpFadeIn(tvLog);
            btnToggleLog.setText(R.string.btn_hide_log);
        } else {
            tvLog.setVisibility(View.GONE);
            btnToggleLog.setText(R.string.btn_show_log);
        }
    }

    /** Hiển thị 3 lần lịch sử gần nhất lên giao diện */
    private void renderHistory() {
        List<OptimizationHistory.Entry> entries = history.getEntries();
        if (entries.isEmpty()) {
            tvHistory.setText("Chưa có lịch sử tối ưu nào.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (OptimizationHistory.Entry e : entries) {
            sb.append("• ").append(e.timestamp)
                    .append(" - Điểm: ").append(e.score)
                    .append(" - ").append(e.profileSummary)
                    .append("\n");
        }
        tvHistory.setText(sb.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // FIX: guard khi setupLogic() chưa chạy (trường hợp key không hợp lệ ở onCreate
        // đã kickToLogin() và return sớm) - tránh NullPointerException.
        if (licenseManager == null) return;
        // Kiểm tra lại ngay khi quay lại app (ví dụ người dùng vừa alt-tab qua lại
        // và key đã hết hạn trong lúc app ở nền), rồi bắt đầu vòng lặp kiểm tra định kỳ
        if (!licenseManager.isLicenseValid()) {
            kickToLogin();
            return;
        }
        if (tvLicenseStatus != null) {
            licenseCheckHandler.postDelayed(licenseCheckRunnable, LOCAL_CHECK_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        licenseCheckHandler.removeCallbacks(licenseCheckRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        licenseCheckHandler.removeCallbacks(licenseCheckRunnable);
        // FIX: chỉ dọn Shizuku nếu setupLogic() đã thực sự chạy (key hợp lệ)
        if (shellExecutor != null) {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
            shellExecutor.unbindService();
        }
    }
}
