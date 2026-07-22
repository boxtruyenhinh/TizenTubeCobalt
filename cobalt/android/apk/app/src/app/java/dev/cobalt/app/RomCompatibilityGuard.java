// Copyright 2026 BoxtruyenhinhOS™.
//
// Licensed under the Apache License, Version 2.0.

package dev.cobalt.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * Kiểm tra ứng dụng có đang chạy trên ROM Boxtruyenhinh hay không.
 *
 * <p>Chỉ kiểm tra Build.DISPLAY, tương ứng với ro.build.display.id.
 */
public final class RomCompatibilityGuard {

  private static final String TAG =
      "BoxTvRomGuard";

  private static final int BACKGROUND_COLOR =
      Color.rgb(96, 96, 96);

  /*
   * Kiểm tra marker dài trước để log nhận diện đúng tên ROM.
   *
   * Các dạng hợp lệ:
   * - BoxtruyenhinhOS™
   * - BoxtruyenhinhOS
   * - Boxtruyenhinh
   */
  private static final String[] SUPPORTED_MARKERS = {
    "boxtruyenhinhos™",
    "boxtruyenhinhos",
    "boxtruyenhinh"
  };

  private RomCompatibilityGuard() {}

  public static boolean isSupportedDevice() {
    return getMatchedMarker() != null;
  }

  public static String getMatchedMarker() {
    String buildDisplay = Build.DISPLAY;

    if (buildDisplay == null
        || buildDisplay.trim().isEmpty()) {
      Log.e(
          TAG,
          "Build.DISPLAY is empty");

      return null;
    }

    String normalizedDisplay =
        buildDisplay
            .trim()
            .toLowerCase(Locale.ROOT);

    for (String marker : SUPPORTED_MARKERS) {
      if (normalizedDisplay.contains(marker)) {
        Log.i(
            TAG,
            "Supported ROM. Build.DISPLAY="
                + buildDisplay
                + ", marker="
                + marker);

        return marker;
      }
    }

    Log.e(
        TAG,
        "Unsupported ROM. Build.DISPLAY="
            + buildDisplay);

    return null;
  }

  /**
   * Hiển thị màn hình xám toàn màn hình.
   *
   * <p>Không tải giao diện YouTube. Người dùng chỉ có thể
   * nhấn nút đóng hoặc nút Back để thoát ứng dụng.
   */
  public static void showUnsupportedScreen(
      Activity activity) {
    if (activity == null
        || activity.isFinishing()
        || activity.isDestroyed()) {
      return;
    }

    Window window = activity.getWindow();

    window.addFlags(
        WindowManager.LayoutParams
            .FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

    window.setBackgroundDrawable(
        new ColorDrawable(BACKGROUND_COLOR));

    window.setStatusBarColor(
        BACKGROUND_COLOR);

    window.setNavigationBarColor(
        BACKGROUND_COLOR);

    LinearLayout root =
        new LinearLayout(activity);

    root.setOrientation(
        LinearLayout.VERTICAL);

    root.setGravity(
        Gravity.CENTER);

    root.setBackgroundColor(
        BACKGROUND_COLOR);

    int horizontalPadding =
        dpToPx(activity, 48);

    int verticalPadding =
        dpToPx(activity, 32);

    root.setPadding(
        horizontalPadding,
        verticalPadding,
        horizontalPadding,
        verticalPadding);

    TextView title =
        new TextView(activity);

    title.setText("Thông báo");
    title.setTextColor(Color.WHITE);

    title.setTextSize(
        TypedValue.COMPLEX_UNIT_SP,
        28);

    title.setGravity(Gravity.CENTER);

    title.setPadding(
        0,
        0,
        0,
        dpToPx(activity, 24));

    root.addView(
        title,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

    TextView message =
        new TextView(activity);

    message.setText(
        "Thiết bị của bạn không được hỗ trợ "
            + "để chạy ứng dụng này, vui lòng liên hệ:\n\n"
            + "BoxtruyenhinhOS™ - 0913388007\n"
            + "(Facebook - Zalo).\n\n"
            + "Cảm ơn.");

    message.setTextColor(Color.WHITE);

    message.setTextSize(
        TypedValue.COMPLEX_UNIT_SP,
        20);

    message.setGravity(Gravity.CENTER);

    message.setLineSpacing(
        0,
        1.15f);

    message.setPadding(
        0,
        0,
        0,
        dpToPx(activity, 32));

    root.addView(
        message,
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

    Button closeButton =
        new Button(activity);

    closeButton.setText(
        "Đóng ứng dụng");

    closeButton.setTextSize(
        TypedValue.COMPLEX_UNIT_SP,
        18);

    closeButton.setAllCaps(false);
    closeButton.setFocusable(true);
    closeButton.setFocusableInTouchMode(true);

    int buttonWidth =
        dpToPx(activity, 240);

    LinearLayout.LayoutParams buttonParams =
        new LinearLayout.LayoutParams(
            buttonWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT);

    buttonParams.gravity =
        Gravity.CENTER_HORIZONTAL;

    closeButton.setOnClickListener(
        view -> closeApplication(activity));

    root.addView(
        closeButton,
        buttonParams);

    activity.setContentView(
        root,
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

    closeButton.post(
        closeButton::requestFocus);
  }

  public static void closeApplication(
      Activity activity) {
    if (activity == null) {
      return;
    }

    try {
      activity.finishAffinity();

      if (Build.VERSION.SDK_INT
          >= Build.VERSION_CODES.LOLLIPOP) {
        activity.finishAndRemoveTask();
      }

      activity.overridePendingTransition(
          0,
          0);
    } catch (RuntimeException error) {
      Log.e(
          TAG,
          "Cannot close unsupported application",
          error);

      activity.finish();
    }
  }

  private static int dpToPx(
      Activity activity,
      int dp) {
    float density =
        activity
            .getResources()
            .getDisplayMetrics()
            .density;

    return Math.round(
        dp * density);
  }
}
