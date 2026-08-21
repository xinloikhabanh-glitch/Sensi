package com.sensi.analytics;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * LoginActivity là màn hình ĐẦU TIÊN người dùng thấy khi mở app (xem
 * AndroidManifest.xml - đây mới là LAUNCHER, không phải MainActivity).
 *
 * Việc kích hoạt key giờ gọi thẳng lên server (ApiClient.validateKey) thay
 * vì tự kiểm tra offline - server mới là nơi quyết định key còn hợp lệ hay
 * không (có thể đã bị xoá/thu hồi/khoá vào máy khác).
 */
public class LoginActivity extends AppCompatActivity {

    private EditText edtKey;
    private TextView tvError;
    private TextView tvStatus;
    private Button btnActivate;
    private ProgressBar progressBar;

    private LicenseManager licenseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        licenseManager = new LicenseManager(this);

        edtKey = findViewById(R.id.edtKey);
        tvError = findViewById(R.id.tvError);
        tvStatus = findViewById(R.id.tvStatus);
        btnActivate = findViewById(R.id.btnActivate);
        progressBar = findViewById(R.id.progressBarLogin);

        btnActivate.setOnClickListener(v -> attemptActivate());

        // Nếu máy đã có key hợp lệ còn hạn (trong thời gian ân hạn offline) -> vào thẳng
        if (licenseManager.isLicenseValid()) {
            goToMain();
            return;
        }

        // Nếu có key cũ nhưng đã hết hạn/hết ân hạn -> hiện rõ lý do bị đưa về đây
        if (licenseManager.getSavedExpiryEpoch() > 0) {
            tvStatus.setText("Key trước đó (" + licenseManager.getSavedTierLabel() + ") đã hết hạn hoặc cần xác thực lại. Vui lòng nhập key.");
        }
    }

    private void attemptActivate() {
        String input = edtKey.getText().toString().trim();
        if (input.isEmpty()) {
            tvError.setText("Vui lòng nhập key");
            return;
        }

        setLoading(true);
        String deviceId = licenseManager.getDeviceId(this);

        ApiClient.validateKey(input, deviceId, response -> {
            setLoading(false);

            if (response.networkError) {
                tvError.setText("Không kết nối được server. Kiểm tra mạng và thử lại.");
                return;
            }

            if (!response.valid) {
                tvError.setText(response.message);
                return;
            }

            licenseManager.saveVerifiedLicense(input, response.tier, response.expiresAt);
            tvError.setText("");
            Toast.makeText(this, "Kích hoạt thành công", Toast.LENGTH_SHORT).show();
            goToMain();
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnActivate.setEnabled(!loading);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish(); // không cho quay lại màn Login bằng nút Back
    }
}
