package com.gathin.tvhome;

import android.app.Activity;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A remote-friendly, cached hourly forecast view for the next 24 hours. */
public final class WeatherForecastActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private boolean observing;
    private final ContentObserver observer = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { render(); }
    };

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF10243A, 0xFF08111F, 0xFF050A12}));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(28), dp(56), dp(28));
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(PrayerTimesRepository.CONTENT_URI, false, observer);
        observing = true;
        PrayerTimesRepository.refreshWeatherIfNeeded(getApplicationContext());
        if (getSharedPreferences("prayer_times", MODE_PRIVATE).getString("weather.hourly", "[]").equals("[]"))
            PrayerTimesRepository.refreshWeatherNow(getApplicationContext());
        render();
    }

    @Override protected void onPause() {
        super.onPause();
        if (observing) getContentResolver().unregisterContentObserver(observer);
        observing = false;
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        TextView title = text(getString(R.string.weather_forecast_title), 32, 0xFFFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        content.addView(title);
        TextView subtitle = text(getString(R.string.weather_forecast_subtitle), 16, 0xB3FFFFFF, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-2, -2);
        subtitleLp.topMargin = dp(5); subtitleLp.bottomMargin = dp(18); content.addView(subtitle, subtitleLp);

        String json = "[]", place = "";
        long updated = 0;
        try (Cursor cursor = getContentResolver().query(PrayerTimesRepository.CONTENT_URI, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                json = value(cursor, "weather_hourly"); place = value(cursor, "place");
                try { updated = Long.parseLong(value(cursor, "weather_updated")); } catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        if (!place.isEmpty()) {
            TextView city = text(place, 17, 0xFFFFD37A, Typeface.create("sans-serif-medium", Typeface.NORMAL));
            content.addView(city);
        }
        JSONArray hours;
        try { hours = new JSONArray(json); } catch (Exception e) { hours = new JSONArray(); }
        if (hours.length() == 0) {
            boolean locationReady = !getSharedPreferences("prayer_times", MODE_PRIVATE).getString("latitude", "").trim().isEmpty()
                    && !getSharedPreferences("prayer_times", MODE_PRIVATE).getString("longitude", "").trim().isEmpty();
            TextView empty = text(getString(locationReady ? R.string.weather_forecast_loading : R.string.weather_setup_home),
                    20, 0xCCFFFFFF, Typeface.create("sans-serif", Typeface.NORMAL));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.topMargin = dp(30); content.addView(empty, lp);
            return;
        }
        TextView freshness = text(updated > 0 ? getString(R.string.weather_last_updated,
                new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new java.util.Date(updated)))
                : getString(R.string.weather_forecast_cached), 13, 0x88FFFFFF, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams freshnessLp = new LinearLayout.LayoutParams(-2, -2);
        freshnessLp.topMargin = dp(4); freshnessLp.bottomMargin = dp(14); content.addView(freshness, freshnessLp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setRowCount(4);
        LocalDateTime now = LocalDateTime.now();
        int start = 0;
        DateTimeFormatter parser = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        for (int i = 0; i < hours.length(); i++) {
            try { if (!LocalDateTime.parse(hours.getJSONObject(i).getString("time"), parser).isBefore(now.withMinute(0).withSecond(0).withNano(0))) { start = i; break; } }
            catch (Exception ignored) { }
        }
        int count = Math.min(24, Math.max(0, hours.length() - start));
        for (int i = 0; i < count; i++) {
            JSONObject hour = hours.optJSONObject(start + i);
            if (hour == null) continue;
            GridLayout.LayoutParams cell = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 6, 1f), GridLayout.spec(i % 6, 1f));
            cell.width = 0; cell.height = dp(150); cell.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(hourCard(hour, parser), cell);
        }
        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        scrollFocusFirst(grid);
    }

    private void scrollFocusFirst(GridLayout grid) {
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private View hourCard(JSONObject item, DateTimeFormatter parser) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true); card.setClickable(true);
        card.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0x6635485F); bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x335D7E9F); card.setBackground(bg);
        ImageView icon = new ImageView(this);
        int code = item.optInt("code", -1);
        icon.setImageResource(icon(code));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(34), dp(34)); iconLp.bottomMargin = dp(4); card.addView(icon, iconLp);
        String time = item.optString("time", "");
        String label = time.length() >= 16 ? time.substring(11, 16) : time;
        try { label = LocalDateTime.parse(time, parser).format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())); }
        catch (Exception ignored) { }
        card.addView(text(label, 16, 0xFFDAE5F2, Typeface.create("sans-serif-medium", Typeface.NORMAL)));
        card.addView(text(item.optString("temperature", "—") + "°", 24, 0xFFFFFFFF, Typeface.create("sans-serif-light", Typeface.NORMAL)));
        card.addView(text(getString(R.string.weather_rain_probability, item.optInt("rain", 0)), 12, 0x99DCEBFA, Typeface.create("sans-serif", Typeface.NORMAL)));
        return card;
    }

    private int icon(int code) {
        if (code == 0) return R.drawable.weather_sun;
        if (code == 1 || code == 2) return R.drawable.weather_partly;
        if (code == 3) return R.drawable.weather_cloud;
        if (code == 45 || code == 48) return R.drawable.weather_fog;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.drawable.weather_snow;
        if (code >= 95) return R.drawable.weather_storm;
        return R.drawable.weather_rain;
    }

    private TextView text(String value, int sp, int color, Typeface face) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color);
        view.setTypeface(face); view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(false); return view;
    }
    private String value(Cursor cursor, String column) { int index = cursor.getColumnIndex(column); return index < 0 ? "" : cursor.getString(index); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
