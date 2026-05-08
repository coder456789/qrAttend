package com.qrattend.app.utils;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.qrattend.app.R;

/**
 * Modern replacement for plain Android Toasts.
 * <p>
 * Uses Material {@link Snackbar} with rounded corners, coloured backgrounds,
 * and white text — much nicer than the default black pill Toast.
 * </p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * SnackbarHelper.success(this, "Attendance marked!");
 * SnackbarHelper.error(this, "Permission denied.");
 * SnackbarHelper.info(this, "Session ended.");
 * SnackbarHelper.warning(this, "GPS accuracy is poor.");
 * }</pre>
 */
public final class SnackbarHelper {

    private SnackbarHelper() {}

    // ── Public entry points ───────────────────────────────────────────────

    /** Green Snackbar — use for successful operations. */
    public static void success(@NonNull Activity activity, @NonNull String message) {
        show(activity, "✅  " + message, R.color.attendanceHigh);
    }

    /** Red Snackbar — use for errors and rejections. */
    public static void error(@NonNull Activity activity, @NonNull String message) {
        show(activity, "❌  " + message, R.color.attendanceLow);
    }

    /** Blue/primary Snackbar — use for neutral info. */
    public static void info(@NonNull Activity activity, @NonNull String message) {
        show(activity, "ℹ️  " + message, R.color.primary);
    }

    /** Amber Snackbar — use for warnings. */
    public static void warning(@NonNull Activity activity, @NonNull String message) {
        show(activity, "⚠️  " + message, R.color.attendanceMid);
    }

    // ── View-based variants (for Fragments or Views without Activity) ─────

    public static void success(@NonNull View anchor, @NonNull String message) {
        show(anchor, "✅  " + message, R.color.attendanceHigh);
    }

    public static void error(@NonNull View anchor, @NonNull String message) {
        show(anchor, "❌  " + message, R.color.attendanceLow);
    }

    public static void info(@NonNull View anchor, @NonNull String message) {
        show(anchor, "ℹ️  " + message, R.color.primary);
    }

    public static void warning(@NonNull View anchor, @NonNull String message) {
        show(anchor, "⚠️  " + message, R.color.attendanceMid);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private static void show(@NonNull Activity activity,
                             @NonNull String message,
                             int colorRes) {
        View root = activity.getWindow().getDecorView().getRootView();
        show(root, message, colorRes);
    }

    private static void show(@NonNull View anchor,
                             @NonNull String message,
                             int colorRes) {
        Snackbar snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT);

        // Style the Snackbar view
        View snackView = snackbar.getView();

        // Rounded background via padding + background tint
        snackView.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(anchor.getContext(), colorRes)));

        // Round the corners (works on API 21+)
        snackView.setBackground(buildRoundedBackground(anchor, colorRes));

        // Margins so it floats above the bottom
        android.view.ViewGroup.MarginLayoutParams params =
                (android.view.ViewGroup.MarginLayoutParams) snackView.getLayoutParams();
        int margin = (int) (16 * anchor.getContext().getResources().getDisplayMetrics().density);
        params.setMargins(margin, margin, margin, margin * 4);
        snackView.setLayoutParams(params);

        // White bold text
        TextView tv = snackView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14f);
            tv.setMaxLines(4);
        }

        snackbar.show();
    }

    private static android.graphics.drawable.Drawable buildRoundedBackground(
            @NonNull View anchor, int colorRes) {
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        float radius = 16 * anchor.getContext().getResources().getDisplayMetrics().density;
        shape.setCornerRadius(radius);
        shape.setColor(ContextCompat.getColor(anchor.getContext(), colorRes));
        return shape;
    }
}
