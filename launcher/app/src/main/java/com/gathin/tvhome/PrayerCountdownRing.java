package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Compact golden progress ring used by the home prayer card. */
public final class PrayerCountdownRing extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int minutes = 0;
    private float fraction = 0.35f;

    public PrayerCountdownRing(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public PrayerCountdownRing(Context context) { super(context); init(); }

    private void init() {
        track.setStyle(Paint.Style.STROKE); track.setStrokeWidth(dp(5)); track.setColor(0x55FFFFFF);
        progress.setStyle(Paint.Style.STROKE); progress.setStrokeWidth(dp(5)); progress.setStrokeCap(Paint.Cap.ROUND);
        progress.setColor(0xFFFFC94D);
        number.setColor(0xFFFFFFFF); number.setTextSize(dp(25)); number.setTextAlign(Paint.Align.CENTER);
        number.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        unit.setColor(0xCCFFFFFF); unit.setTextSize(dp(11)); unit.setTextAlign(Paint.Align.CENTER);
    }

    void setCountdown(int minutesRemaining, int totalMinutes) {
        minutes = Math.max(0, minutesRemaining);
        fraction = totalMinutes <= 0 ? 0.15f : Math.max(0.05f, Math.min(1f, minutes / (float) totalMinutes));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = dp(7);
        arc.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(arc, -90, 360, false, track);
        canvas.drawArc(arc, -90, -360 * fraction, false, progress);
        float centerX = getWidth() / 2f, centerY = getHeight() / 2f;
        canvas.drawText(String.valueOf(minutes), centerX, centerY + dp(2), number);
        canvas.drawText(getResources().getQuantityString(R.plurals.countdown_minutes_unit, minutes),
                centerX, centerY + dp(19), unit);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
