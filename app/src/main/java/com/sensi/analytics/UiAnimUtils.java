package com.sensi.analytics;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * UiAnimUtils gom các hiệu ứng chuyển động (animation) dùng lại nhiều lần
 * trong MainActivity, giúp giao diện "mượt" hơn thay vì hiện/ẩn đột ngột.
 * Toàn bộ chỉ dùng View Property Animator có sẵn của Android, không cần
 * thư viện animation ngoài.
 */
public final class UiAnimUtils {

    private UiAnimUtils() {
    }

    /** Hiệu ứng trượt lên + mờ dần hiện ra — dùng cho CardView kết quả/thông tin thiết bị */
    public static void slideUpFadeIn(View view) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(40f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    /** Hiệu ứng mờ dần hiện/ẩn — dùng cho thanh loading */
    public static void fadeVisibility(View view, boolean show) {
        if (show) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(200).start();
        } else {
            view.animate().alpha(0f).setDuration(200)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            view.setVisibility(View.GONE);
                        }
                    }).start();
        }
    }

    /** Hiệu ứng "nảy" nhẹ khi bấm nút — cảm giác phản hồi thao tác rõ ràng hơn */
    public static void pulse(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f);
        scaleX.setDuration(220);
        scaleY.setDuration(220);
        scaleX.setInterpolator(new OvershootInterpolator());
        scaleY.setInterpolator(new OvershootInterpolator());
        scaleX.start();
        scaleY.start();
    }
}
