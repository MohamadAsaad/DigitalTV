package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.InputType;
import android.annotation.SuppressLint;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.List;

/**
 * Today's prayer times for one city from Aladhan (free, no account or key; only the coordinates and
 * the date are sent), with the calculation method in res/values/config.xml. The next prayer is highlighted
 * with a countdown.
 */
public final class PrayerTimesActivity extends Activity {
    private static final String TAG = "PrayerTimes";
    private static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final String[] ARABIC = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] ICONS = {R.drawable.ic_fajr, R.drawable.ic_sunrise, R.drawable.ic_dhuhr,
            R.drawable.ic_asr, R.drawable.ic_maghrib, R.drawable.ic_isha};
    private static final long MINUTE_MS = 60_000L;
    private static final int LOCATION_PERMISSION_REQUEST = 511;
    private static final long LOCATION_TIMEOUT_MS = 20_000L;
    private static final long MAX_LAST_LOCATION_AGE_MS = 24 * 60 * 60_000L;
    private static final City[] CITIES = {
            new City(41.0082, 28.9784), new City(39.9334, 32.8597), new City(38.4237, 27.1428),
            new City(40.1950, 29.0600), new City(36.8969, 30.7133), new City(21.3891, 39.8579),
            new City(24.4672, 39.6111), new City(24.7136, 46.6753), new City(21.4858, 39.1925),
            new City(30.0444, 31.2357), new City(31.2001, 29.9187), new City(33.5138, 36.2765),
            new City(31.9539, 35.9106), new City(33.8938, 35.5018), new City(25.2048, 55.2708),
            new City(25.2854, 51.5310), new City(29.3759, 47.9774), new City(33.3152, 44.3661),
            new City(33.5731, -7.5898), new City(36.8065, 10.1815), new City(51.5072, -0.1276),
            new City(48.8566, 2.3522), new City(52.5200, 13.4050), new City(40.7128, -74.0060),
            new City(43.6532, -79.3832), new City(28.6139, 77.2090), new City(24.8607, 67.0011),
            new City(3.1390, 101.6869), new City(-6.2088, 106.8456)
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface font;
    private int accent;
    private TextView dates, next, status, alertChoice, placeLabel;
    private LinearLayout rows;
    /** Today's times as minutes after midnight, in NAMES order; null until loaded. */
    private int[] minutes;
    private String[] times;
    /** The calculation method's name as Aladhan gives it, for the status line. */
    private String methodName = "";
    /** The day the times are for (yyyy-MM-dd), to reload after midnight. */
    private String loadedFor;
    private boolean loading;
    private boolean observerRegistered;
    private LocationListener locationListener;
    private final Runnable locationTimeout = () -> {
        stopLocationUpdates();
        status.setText(R.string.prayer_app_location_not_found);
    };
    private final ContentObserver scheduleObserver = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { load(); }
    };

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!today().equals(loadedFor)) load();
            else render();
            main.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        font = getResources().getFont(R.font.outfit);
        accent = getColor(R.color.prayer_app_accent);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {(accent & 0x00FFFFFF) | 0x55000000, 0xFF050607, 0xFF050607}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(80), dp(48), dp(80), dp(40));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        placeLabel = text(18, 400, 0xB3FFFFFF);
        placeLabel.setLetterSpacing(0.25f);
        placeLabel.setFocusable(true); placeLabel.setClickable(true);
        placeLabel.setOnClickListener(v -> showLocationPicker());
        col.addView(placeLabel);
        updatePlaceLabel();

        alertChoice = text(16, 500, 0xFF7CD4FF);
        alertChoice.setFocusable(true); alertChoice.setClickable(true);
        alertChoice.setPadding(0, dp(14), 0, dp(8));
        alertChoice.setOnClickListener(v -> showAlertPicker());
        col.addView(alertChoice);

        next = text(52, 300, 0xFFFFFFFF);
        next.setPadding(0, dp(10), 0, dp(4));
        col.addView(next);
        dates = text(20, 400, 0xB3FFFFFF);
        col.addView(dates);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        rlp.setMargins(0, dp(36), 0, 0);
        col.addView(rows, rlp);

        status = text(16, 400, 0x80FFFFFF);
        status.setPadding(0, dp(18), 0, 0);
        status.setText("Loading…");
        col.addView(status);

        setContentView(root);
        if (getSharedPreferences("prayer_times", MODE_PRIVATE).getString("location", "").trim().isEmpty())
            root.post(this::showLocationPicker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAlertLabel();
        if (!observerRegistered) {
            getContentResolver().registerContentObserver(PrayerTimesRepository.CONTENT_URI, false, scheduleObserver);
            observerRegistered = true;
        }
        load();
        main.postDelayed(tick, MINUTE_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
        stopLocationUpdates();
        if (observerRegistered) {
            getContentResolver().unregisterContentObserver(scheduleObserver);
            observerRegistered = false;
        }
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    private void load() {
        if (loading) return;
        loading = true;
        if (getSharedPreferences("prayer_times", MODE_PRIVATE).getString("latitude", "").trim().isEmpty()) {
            loading = false;
            status.setText(R.string.prayer_app_location_choose);
            return;
        }
        PrayerTimesRepository.refreshIfNeeded(this);
        PrayerTimesRepository.refreshWeatherIfNeeded(getApplicationContext());
        PrayerTimesRepository.Snapshot data = PrayerTimesRepository.read(this);
        loading = false;
        if (data == null) {
            status.setText(R.string.prayer_app_prayer_loading);
            return;
        }
        try {
            parse(data);
            PrayerAlertScheduler.scheduleNext(this);
            loadedFor = data.date;
            render();
        } catch (Exception e) {
            Log.w(TAG, "parse: " + e);
            status.setText(R.string.prayer_app_prayer_unexpected);
        }
    }

    private void updatePlaceLabel() {
        String name = getSharedPreferences("prayer_times", MODE_PRIVATE).getString("location", "").trim();
        placeLabel.setText((name.isEmpty() ? getString(R.string.prayer_app_location_choose) : name).toUpperCase(Locale.ROOT));
    }

    private void showLocationPicker() {
        String[] choices = {
                getString(R.string.prayer_app_location_use_current),
                getString(R.string.prayer_app_location_choose_city),
                getString(R.string.prayer_app_location_manual)
        };
        new AlertDialog.Builder(this).setTitle(R.string.prayer_app_location_setup_title)
                .setMessage(R.string.prayer_app_location_privacy)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) useCurrentLocation();
                    else if (which == 1) showCityPicker();
                    else showManualLocationPicker();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showCityPicker() {
        String[] cities = getResources().getStringArray(R.array.prayer_app_cities);
        new AlertDialog.Builder(this).setTitle(R.string.prayer_app_location_choose_city)
                .setItems(cities, (dialog, which) -> {
                    if (which >= 0 && which < CITIES.length && which < cities.length) {
                        City city = CITIES[which];
                        saveLocation(cities[which], city.latitude, city.longitude, null);
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void useCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            status.setText(R.string.prayer_app_location_permission_request);
            return;
        }
        detectCurrentLocation();
    }

    @SuppressLint("MissingPermission")
    private void detectCurrentLocation() {
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            status.setText(R.string.prayer_app_location_not_found);
            return;
        }
        Location best = null;
        List<String> providers;
        try {
            providers = manager.getProviders(true);
            for (String provider : providers) {
                try {
                    Location last = manager.getLastKnownLocation(provider);
                    if (isRecent(last) && (best == null || last.getTime() > best.getTime())) best = last;
                } catch (SecurityException ignored) { }
            }
        } catch (SecurityException e) {
            status.setText(R.string.prayer_app_location_permission_denied);
            return;
        }
        if (best != null) {
            acceptCurrentLocation(best);
            return;
        }
        if (providers.isEmpty()) {
            status.setText(R.string.prayer_app_location_not_found);
            return;
        }
        stopLocationUpdates();
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { acceptCurrentLocation(location); }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
            @Override public void onStatusChanged(String provider, int providerStatus, Bundle extras) { }
        };
        int requestedProviders = 0;
        try {
            for (String provider : providers) {
                try {
                    manager.requestLocationUpdates(provider, 0L, 0f, locationListener, Looper.getMainLooper());
                    requestedProviders++;
                }
                catch (SecurityException | IllegalArgumentException ignored) { }
            }
            if (requestedProviders == 0) {
                stopLocationUpdates();
                status.setText(R.string.prayer_app_location_not_found);
                return;
            }
            status.setText(R.string.prayer_app_location_detecting);
            main.postDelayed(locationTimeout, LOCATION_TIMEOUT_MS);
        } catch (SecurityException e) {
            stopLocationUpdates();
            status.setText(R.string.prayer_app_location_permission_denied);
        }
    }

    private boolean isRecent(Location location) {
        if (location == null) return false;
        long age = System.currentTimeMillis() - location.getTime();
        return age >= 0 && age <= MAX_LAST_LOCATION_AGE_MS;
    }

    private void acceptCurrentLocation(Location location) {
        stopLocationUpdates();
        saveLocation(getString(R.string.prayer_app_location_current), location.getLatitude(), location.getLongitude(), null);
    }

    private void stopLocationUpdates() {
        main.removeCallbacks(locationTimeout);
        if (locationListener == null) return;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager != null) {
            try { manager.removeUpdates(locationListener); } catch (SecurityException ignored) { }
        }
        locationListener = null;
    }

    private void saveLocation(String name, double latitude, double longitude, String method) {
        android.content.SharedPreferences prefs = getSharedPreferences("prayer_times", MODE_PRIVATE);
        String savedMethod = method;
        if (savedMethod == null || savedMethod.isEmpty()) {
            savedMethod = prefs.getString("method", "");
            if (savedMethod.isEmpty()) savedMethod = defaultMethod(latitude, longitude);
        }
        prefs.edit().putString("location", name)
                .putString("latitude", String.format(Locale.US, "%.6f", latitude))
                .putString("longitude", String.format(Locale.US, "%.6f", longitude))
                .putString("method", savedMethod)
                .remove("day.date").remove("next.date").remove("attempt.date").remove("weather.date")
                .putLong("weather.updated", 0L).putLong("weather.attempt", 0L).apply();
        PrayerAlertScheduler.scheduleNext(this);
        updatePlaceLabel();
        status.setText(R.string.prayer_app_location_saved);
        load();
    }

    private String defaultMethod(double latitude, double longitude) {
        return latitude >= 35.8 && latitude <= 42.2 && longitude >= 26.0 && longitude <= 44.8
                ? "13" : "3";
    }

    private void showManualLocationPicker() {
        android.content.SharedPreferences prefs = getSharedPreferences("prayer_times", MODE_PRIVATE);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12); form.setPadding(pad, pad, pad, 0);
        EditText place = field(form, R.string.prayer_app_location_name_label, prefs.getString("location", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText latitude = field(form, R.string.prayer_app_location_latitude_label, prefs.getString("latitude", ""), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText longitude = field(form, R.string.prayer_app_location_longitude_label, prefs.getString("longitude", ""), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView methodTitle = text(16, 500, 0xFFFFFFFF); methodTitle.setText(R.string.prayer_app_location_method_label); form.addView(methodTitle);
        Spinner method = new Spinner(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.prayer_app_calculation_method_names, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); method.setAdapter(adapter); form.addView(method);
        TextView privacy = text(14, 400, 0x99FFFFFF);
        privacy.setPadding(0, dp(10), 0, 0); privacy.setText(R.string.prayer_app_location_privacy); form.addView(privacy);
        int[] ids = getResources().getIntArray(R.array.prayer_app_calculation_method_ids);
        String selected = prefs.getString("method", defaultMethod(
                parseCoordinate(prefs.getString("latitude", ""), 0d), parseCoordinate(prefs.getString("longitude", ""), 0d)));
        for (int i = 0; i < ids.length; i++) if (String.valueOf(ids[i]).equals(selected)) method.setSelection(i);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.prayer_app_location_setup_title).setView(form)
                .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String lat = latitude.getText().toString().trim(), lon = longitude.getText().toString().trim();
            double latValue, lonValue;
            try { latValue = Double.parseDouble(lat); lonValue = Double.parseDouble(lon); }
            catch (NumberFormatException ex) { latitude.setError(getString(R.string.prayer_app_location_invalid)); return; }
            String name = place.getText().toString().trim();
            if (name.isEmpty() || !Double.isFinite(latValue) || !Double.isFinite(lonValue)
                    || latValue < -90 || latValue > 90 || lonValue < -180 || lonValue > 180) {
                place.setError(getString(R.string.prayer_app_location_invalid)); return;
            }
            int choice = Math.max(0, method.getSelectedItemPosition());
            saveLocation(name, latValue, lonValue, String.valueOf(ids[Math.min(choice, ids.length - 1)]));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static double parseCoordinate(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return fallback; }
    }

    private static final class City {
        final double latitude, longitude;
        City(double latitude, double longitude) { this.latitude = latitude; this.longitude = longitude; }
    }

    private EditText field(LinearLayout form, int label, String value, int type) {
        TextView title = text(16, 500, 0xFFFFFFFF); title.setText(label); form.addView(title);
        EditText input = new EditText(this); input.setSingleLine(true); input.setInputType(type); input.setText(value);
        input.setSelectAllOnFocus(true); form.addView(input); return input;
    }

    private void showAlertPicker() {
        String[] labels = {getString(R.string.off), getString(R.string.prayer_app_prayer_alert_10),
                getString(R.string.prayer_app_prayer_alert_15), getString(R.string.prayer_app_prayer_alert_30)};
        int current = getSharedPreferences(PrayerAlertScheduler.PREFS, MODE_PRIVATE).getBoolean("enabled", false)
                ? getSharedPreferences(PrayerAlertScheduler.PREFS, MODE_PRIVATE).getInt("minutes", 10) : 0;
        int checked = current == 10 ? 1 : current == 15 ? 2 : current == 30 ? 3 : 0;
        new AlertDialog.Builder(this).setTitle(R.string.prayer_app_prayer_alert_label).setSingleChoiceItems(labels, checked, (dialog, which) -> {
            dialog.dismiss(); int minutes = which == 0 ? 0 : which == 1 ? 10 : which == 2 ? 15 : 30;
            if (minutes == 0) { PrayerAlertScheduler.set(this, 0); updateAlertLabel(); return; }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 510);
                getSharedPreferences(PrayerAlertScheduler.PREFS, MODE_PRIVATE).edit().putInt("pending_minutes", minutes).apply();
                return;
            }
            PrayerAlertScheduler.set(this, minutes); updateAlertLabel();
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void updateAlertLabel() {
        int minutes = getSharedPreferences(PrayerAlertScheduler.PREFS, MODE_PRIVATE).getBoolean("enabled", false)
                ? getSharedPreferences(PrayerAlertScheduler.PREFS, MODE_PRIVATE).getInt("minutes", 10) : 0;
        alertChoice.setText(getString(R.string.prayer_app_prayer_alert_state, minutes == 0 ? getString(R.string.off)
                : getString(R.string.prayer_app_prayer_alert_before, minutes)));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                detectCurrentLocation();
            } else status.setText(R.string.prayer_app_location_permission_denied);
            return;
        }
        if (requestCode != 510) return;
        int pending = getSharedPreferences(PrayerAlertScheduler.PREFS, MODE_PRIVATE).getInt("pending_minutes", 0);
        getSharedPreferences(PrayerAlertScheduler.PREFS, MODE_PRIVATE).edit().remove("pending_minutes").apply();
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && pending > 0) {
            PrayerAlertScheduler.set(this, pending); updateAlertLabel();
        } else status.setText(R.string.prayer_app_prayer_alert_permission);
    }


    private void parse(PrayerTimesRepository.Snapshot data) throws Exception {
        int[] m = new int[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            String v = data.times[i];
            m[i] = Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3, 5));
        }
        minutes = m;
        times = data.times;
        methodName = data.method;
        String day = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(new Date());
        dates.setText(data.hijri.isEmpty() ? day : day + "  ·  " + data.hijri);
    }

    private void render() {
        if (minutes == null) return;
        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int nextIdx = -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (i == 1) continue;   // sunrise is shown but is not a prayer
            if (minutes[i] > now) { nextIdx = i; break; }
        }
        if (nextIdx >= 0) {
            int left = minutes[nextIdx] - now;
            next.setText(getString(R.string.prayer_app_prayer_next_in, ARABIC[nextIdx],
                    (left >= 60 ? left / 60 + " س " : "") + left % 60 + " د"));
        } else {
            next.setText(R.string.prayer_app_prayer_fajr_tomorrow);
        }

        rows.removeAllViews();
        for (int i = 0; i < NAMES.length; i++) {
            boolean hl = i == nextIdx;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(6), dp(20), dp(6), dp(20));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(22));
            bg.setColor(hl ? accent : 0x14FFFFFF);
            card.setBackground(bg);
            int dim = i == 1 ? 0x80FFFFFF : 0xFFFFFFFF;
            int fg = hl ? 0xFFFFFFFF : dim;
            ImageView icon = new ImageView(this);
            icon.setImageResource(ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(hl ? 0xFFFFFFFF : (i == 1 ? 0x80FFFFFF : accent)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
            ilp.bottomMargin = dp(22);
            card.addView(icon, ilp);
            TextView ar = centered(text(26, 400, fg));
            ar.setText(ARABIC[i]);
            card.addView(ar);
            TextView name = centered(text(20, 500, hl ? 0xE6FFFFFF : (i == 1 ? 0x80FFFFFF : 0xB3FFFFFF)));
            name.setText(NAMES[i]);
            name.setPadding(0, dp(8), 0, dp(20));
            card.addView(name);
            TextView time = centered(text(42, hl ? 500 : 300, fg));
            time.setText(times[i]);
            // Shrink rather than cut "05:10" when the card is narrow.
            time.setMaxLines(1);
            time.setAutoSizeTextTypeUniformWithConfiguration(20, 42, 1, TypedValue.COMPLEX_UNIT_SP);
            card.addView(time, new LinearLayout.LayoutParams(-1, dp(52)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(i == 0 ? 0 : dp(8), 0, i == NAMES.length - 1 ? 0 : dp(8), 0);
            rows.addView(card, lp);
        }
        status.setText(getString(R.string.prayer_app_prayer_updated,
                new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date()), methodName));
    }

    /** Centred on its own line, whatever the script direction. */
    private static TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        return t;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
