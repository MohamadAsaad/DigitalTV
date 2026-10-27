package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches prayer times for the user's configured location and calculation method, then serves the local cache. */
final class PrayerTimesRepository {
    static final Uri CONTENT_URI = Uri.parse("content://com.gathin.tvhome.prayer.times/today");
    static final String[] COLUMNS = {"date", "fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha",
            "tomorrow_fajr", "place", "method", "hijri", "weather_date", "weather_temp", "weather_code",
            "weather_max", "weather_min", "weather_rain", "weather_error", "weather_updated",
            "weather_tomorrow_code", "weather_tomorrow_max", "weather_tomorrow_min"};
    private static final String TAG = "PrayerTimes";
    private static final String PREFS = "prayer_times";
    private static final String[] KEYS = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};
    private static final long RETRY_MS = 15 * 60_000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);
    private static final AtomicBoolean WEATHER_FETCHING = new AtomicBoolean(false);
    private static final long WEATHER_REFRESH_MS = 3 * 60 * 60_000L;

    static final class Snapshot {
        final String date;
        final String[] times;
        final String tomorrowFajr;
        final String place;
        final String method;
        final String hijri;

        Snapshot(String date, String[] times, String tomorrowFajr, String place, String method, String hijri) {
            this.date = date;
            this.times = times;
            this.tomorrowFajr = tomorrowFajr;
            this.place = place;
            this.method = method;
            this.hijri = hijri;
        }
    }

    private static final class Schedule {
        final String date;
        final String[] times;
        final String method;
        final String hijri;

        Schedule(String date, String[] times, String method, String hijri) {
            this.date = date;
            this.times = times;
            this.method = method;
            this.hijri = hijri;
        }
    }

    private PrayerTimesRepository() { }

    static void refreshIfNeeded(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LocalDate today = LocalDate.now();
        String date = today.toString();
        String tomorrow = today.plusDays(1).toString();
        if (date.equals(prefs.getString("day.date", ""))
                && tomorrow.equals(prefs.getString("next.date", ""))) return;
        long now = System.currentTimeMillis();
        if (date.equals(prefs.getString("attempt.date", ""))
                && now - prefs.getLong("attempt.time", 0L) < RETRY_MS) return;
        if (!FETCHING.compareAndSet(false, true)) return;

        prefs.edit().putString("attempt.date", date).putLong("attempt.time", now).apply();
        EXECUTOR.execute(() -> {
            Schedule day = null;
            Schedule next = null;
            try {
                day = fetch(app, date);
                next = fetch(app, tomorrow);
            } catch (Exception e) {
                Log.w(TAG, "Could not refresh prayer times", e);
            }
            SharedPreferences.Editor edit = prefs.edit();
            if (day != null) put(edit, "day.", day);
            if (next != null) put(edit, "next.", next);
            edit.putLong("updated.at", System.currentTimeMillis()).apply();
            FETCHING.set(false);
            PrayerAlertScheduler.scheduleNext(app);
            app.getContentResolver().notifyChange(CONTENT_URI, null);
        });
    }

    static Snapshot read(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = LocalDate.now().toString();
        String prefix;
        if (today.equals(prefs.getString("day.date", ""))) prefix = "day.";
        else if (today.equals(prefs.getString("next.date", ""))) prefix = "next.";
        else return null;

        String[] times = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            times[i] = prefs.getString(prefix + KEYS[i], "");
            if (times[i].isEmpty()) return null;
        }
        String tomorrowFajr = LocalDate.now().plusDays(1).toString().equals(prefs.getString("next.date", ""))
                ? prefs.getString("next.fajr", "") : "";
        return new Snapshot(today, times, tomorrowFajr, prefs.getString("location", context.getString(R.string.prayer_app_location_choose)),
                prefs.getString(prefix + "method", ""), prefs.getString(prefix + "hijri", ""));
    }

    static Object[] row(Context context) {
        refreshIfNeeded(context);
        refreshWeatherIfNeeded(context.getApplicationContext());
        Snapshot data = read(context);
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Object[] row = new Object[COLUMNS.length];
        row[0] = data == null ? "" : data.date;
        for (int i = 0; i < KEYS.length; i++) row[i + 1] = data == null ? "" : data.times[i];
        row[7] = data == null ? "" : data.tomorrowFajr;
        row[8] = data == null ? prefs.getString("location", context.getString(R.string.prayer_app_location_choose)) : data.place;
        row[9] = data == null ? "" : data.method;
        row[10] = data == null ? "" : data.hijri;
        row[11] = prefs.getString("weather.date", "");
        row[12] = prefs.getString("weather.temp", "");
        row[13] = prefs.getString("weather.code", "");
        row[14] = prefs.getString("weather.max", "");
        row[15] = prefs.getString("weather.min", "");
        row[16] = prefs.getString("weather.rain", "");
        row[17] = prefs.getBoolean("weather.error", false) ? "1" : "0";
        row[18] = String.valueOf(prefs.getLong("weather.updated", 0L));
        row[19] = prefs.getString("weather.tomorrow.code", "");
        row[20] = prefs.getString("weather.tomorrow.max", "");
        row[21] = prefs.getString("weather.tomorrow.min", "");
        return row;
    }

    static void refreshWeatherIfNeeded(Context app) {
        refreshWeather(app, false);
    }

    static void refreshWeatherNow(Context app) { refreshWeather(app, true); }

    private static void refreshWeather(Context app, boolean force) {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if ((!force && LocalDate.now().toString().equals(prefs.getString("weather.date", ""))
                && now - prefs.getLong("weather.updated", 0L) < WEATHER_REFRESH_MS)
                || (!force && now - prefs.getLong("weather.attempt", 0L) < RETRY_MS)
                || !WEATHER_FETCHING.compareAndSet(false, true)) return;
        prefs.edit().putLong("weather.attempt", now).apply();
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String lat = prefs.getString("latitude", "").trim();
                String lon = prefs.getString("longitude", "").trim();
                if (lat.isEmpty() || lon.isEmpty()) throw new IllegalStateException("Set your location first");
                String timezone = java.net.URLEncoder.encode(java.time.ZoneId.systemDefault().getId(), "UTF-8");
                String endpoint = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&timezone=" + timezone + "&forecast_days=2";
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "CleanYourTV/1.0 AndroidTV weather-card");
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK)
                    throw new java.io.IOException("Weather service returned HTTP " + responseCode);
                try (InputStream in = connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096];
                    for (int n; (n = in.read(buffer)) > 0;) out.write(buffer, 0, n);
                    JSONObject json = new JSONObject(out.toString("UTF-8"));
                    JSONObject current = json.getJSONObject("current"), daily = json.getJSONObject("daily");
                    String date = daily.getJSONArray("time").getString(0);
                    prefs.edit().putString("weather.date", date)
                            .putString("weather.temp", String.valueOf(Math.round(current.getDouble("temperature_2m"))))
                            .putString("weather.code", String.valueOf(current.getInt("weather_code")))
                            .putString("weather.max", String.valueOf(Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0))))
                            .putString("weather.min", String.valueOf(Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0))))
                            .putString("weather.rain", String.valueOf(daily.getJSONArray("precipitation_probability_max").optInt(0, 0)))
                            .putString("weather.tomorrow.code", String.valueOf(daily.getJSONArray("weather_code").optInt(1, 0)))
                            .putString("weather.tomorrow.max", String.valueOf(Math.round(daily.getJSONArray("temperature_2m_max").getDouble(1))))
                            .putString("weather.tomorrow.min", String.valueOf(Math.round(daily.getJSONArray("temperature_2m_min").getDouble(1))))
                            .putBoolean("weather.error", false)
                            .putLong("weather.updated", System.currentTimeMillis()).apply();
                }
            } catch (Exception e) {
                prefs.edit().putBoolean("weather.error", true).apply();
                Log.w(TAG, "Could not refresh weather: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            finally {
                if (connection != null) connection.disconnect();
                WEATHER_FETCHING.set(false);
                app.getContentResolver().notifyChange(CONTENT_URI, null);
            }
        });
    }

    private static void put(SharedPreferences.Editor edit, String prefix, Schedule schedule) {
        edit.putString(prefix + "date", schedule.date)
                .putString(prefix + "method", schedule.method)
                .putString(prefix + "hijri", schedule.hijri);
        for (int i = 0; i < KEYS.length; i++) edit.putString(prefix + KEYS[i], schedule.times[i]);
    }

    private static Schedule fetch(Context context, String date) throws Exception {
        String apiDate = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT));
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String latitude = prefs.getString("latitude", "").trim();
        String longitude = prefs.getString("longitude", "").trim();
        if (latitude.isEmpty() || longitude.isEmpty()) throw new IllegalStateException("Set your location first");
        String method = prefs.getString("method", "3");
        String url = "https://api.aladhan.com/v1/timings/" + apiDate
                + "?latitude=" + latitude + "&longitude=" + longitude + "&method=" + method;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int count; (count = in.read(buffer)) > 0; ) out.write(buffer, 0, count);
            JSONObject data = new JSONObject(out.toString("UTF-8")).getJSONObject("data");
            JSONObject timing = data.getJSONObject("timings");
            String[] times = new String[KEYS.length];
            for (int i = 0; i < KEYS.length; i++) {
                String value = timing.getString(KEYS[i].substring(0, 1).toUpperCase(Locale.ROOT) + KEYS[i].substring(1)).trim();
                int suffix = value.indexOf(' ');
                times[i] = suffix > 0 ? value.substring(0, suffix) : value;
            }
            JSONObject meta = data.optJSONObject("meta");
            JSONObject methodData = meta != null ? meta.optJSONObject("method") : null;
            String methodName = methodData != null ? methodData.optString("name", "") : "";
            JSONObject hijri = data.optJSONObject("date") != null
                    ? data.getJSONObject("date").optJSONObject("hijri") : null;
            String hijriDate = "";
            if (hijri != null) {
                JSONObject month = hijri.optJSONObject("month");
                hijriDate = hijri.optString("day", "") + " " + (month == null ? "" : month.optString("ar", ""))
                        + " " + hijri.optString("year", "") + " هـ";
            }
            return new Schedule(date, times, methodName, hijriDate);
        } finally {
            connection.disconnect();
        }
    }
}
