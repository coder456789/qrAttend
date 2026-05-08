package com.qrattend.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

import com.qrattend.app.R;

/**
 * A lightweight donut / ring chart drawn entirely on Canvas.
 * No third-party chart library required.
 *
 * <p>Shows an animated arc representing an attendance percentage, with the
 * numeric value centred inside the ring. Segments are coloured by threshold:
 * <ul>
 *   <li>&ge;75% → green   ({@code R.color.attendanceHigh})</li>
 *   <li>&ge;60% → amber   ({@code R.color.attendanceMid})</li>
 *   <li>&lt;60% → red     ({@code R.color.attendanceLow})</li>
 * </ul>
 * </p>
 *
 * Usage in XML:
 * <pre>{@code
 * <com.qrattend.app.ui.AttendanceDonutView
 *     android:id="@+id/donutView"
 *     android:layout_width="180dp"
 *     android:layout_height="180dp" />
 * }</pre>
 */
public class AttendanceDonutView extends View {

    // ── Paints ───────────────────────────────────────────────────────────

    private final Paint trackPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── State ────────────────────────────────────────────────────────────

    /** 0–100 target value set by caller. */
    private int targetPercent = 0;

    /** Animated value that drives the sweep angle (0–100). */
    private float animatedPercent = 0f;

    private final RectF oval = new RectF();

    // ── Constructors ─────────────────────────────────────────────────────

    public AttendanceDonutView(Context context) {
        super(context);
        init(context);
    }

    public AttendanceDonutView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AttendanceDonutView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    // ── Init ─────────────────────────────────────────────────────────────

    private void init(Context ctx) {
        // Track (background ring)
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dpToPx(ctx, 16));
        trackPaint.setColor(ContextCompat.getColor(ctx, R.color.surfaceContainer));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        // Coloured arc
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dpToPx(ctx, 16));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        // Centre percentage text
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        // Sub-label "Attendance"
        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(ContextCompat.getColor(ctx, R.color.textSecondary));
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Sets the attendance percentage and starts the fill animation.
     * @param percent 0–100
     */
    public void setPercent(int percent) {
        this.targetPercent = Math.max(0, Math.min(100, percent));

        ValueAnimator animator = ValueAnimator.ofFloat(0f, targetPercent);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(anim -> {
            animatedPercent = (float) anim.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    // ── Draw ─────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        float strokeWidth = dpToPx(getContext(), 16);
        float inset       = strokeWidth / 2f + dpToPx(getContext(), 4);

        oval.set(inset, inset, w - inset, h - inset);

        // ── 1. Track ──────────────────────────────────────────────────────
        canvas.drawArc(oval, -90, 360, false, trackPaint);

        // ── 2. Arc ───────────────────────────────────────────────────────
        float sweep = (animatedPercent / 100f) * 360f;
        arcPaint.setColor(arcColor(getContext(), targetPercent));
        canvas.drawArc(oval, -90, sweep, false, arcPaint);

        // ── 3. Percentage text ───────────────────────────────────────────
        int displayPct = Math.round(animatedPercent);
        textPaint.setTextSize(w * 0.22f);
        textPaint.setColor(arcColor(getContext(), targetPercent));
        canvas.drawText(displayPct + "%", cx, cy + textPaint.getTextSize() * 0.35f, textPaint);

        // ── 4. Sub-label ─────────────────────────────────────────────────
        labelPaint.setTextSize(w * 0.10f);
        canvas.drawText("Attendance", cx,
                cy + textPaint.getTextSize() * 0.35f + labelPaint.getTextSize() * 1.3f,
                labelPaint);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private int arcColor(Context ctx, int pct) {
        int colorRes = pct >= 75 ? R.color.attendanceHigh
                : pct >= 60      ? R.color.attendanceMid
                :                  R.color.attendanceLow;
        return ContextCompat.getColor(ctx, colorRes);
    }

    private float dpToPx(Context ctx, float dp) {
        return dp * ctx.getResources().getDisplayMetrics().density;
    }
}
