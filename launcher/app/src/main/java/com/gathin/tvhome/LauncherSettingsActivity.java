package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private static final int PICK_WALLPAPER = 41;
    private static final int EXPORT_BACKUP = 43;
    private static final int IMPORT_BACKUP = 44;
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    static final String EXTRA_DOCK_EDIT = "com.gathin.tvhome.extra.DOCK_EDIT_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private TextView homeIdleClockChoice;
    private TextView wallpaperDimChoice, wallpaperRotationChoice, homeCardOrderChoice, clockContentChoice;
    private TextView clockDigitsChoice, clockFormatChoice, clockBrightnessChoice;
    private TextView dockAppsChoice, performanceChoice, backupExportChoice, backupImportChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        homeIdleClockChoice = findViewById(R.id.homeIdleClockChoice);
        homeIdleClockChoice.setOnClickListener(v -> showIdleClockPicker());
        wallpaperDimChoice = findViewById(R.id.wallpaperDimChoice);
        wallpaperDimChoice.setOnClickListener(v -> chooseWallpaperDim());
        wallpaperRotationChoice = findViewById(R.id.wallpaperRotationChoice);
        wallpaperRotationChoice.setOnClickListener(v -> chooseWallpaperRotation());
        homeCardOrderChoice = findViewById(R.id.homeCardOrderChoice);
        homeCardOrderChoice.setOnClickListener(v -> chooseHomeCardOrder());
        TextView prayerSettingsChoice = findViewById(R.id.prayerSettingsChoice);
        prayerSettingsChoice.setOnClickListener(v -> open(new Intent(this, PrayerTimesActivity.class)));
        clockContentChoice = findViewById(R.id.clockContentChoice);
        clockContentChoice.setOnClickListener(v -> chooseClockContent());
        clockDigitsChoice = findViewById(R.id.clockDigitsChoice);
        clockDigitsChoice.setOnClickListener(v -> chooseClockDigits());
        clockFormatChoice = findViewById(R.id.clockFormatChoice);
        clockFormatChoice.setOnClickListener(v -> chooseClockFormat());
        clockBrightnessChoice = findViewById(R.id.clockBrightnessChoice);
        clockBrightnessChoice.setOnClickListener(v -> chooseClockBrightness());
        dockAppsChoice = findViewById(R.id.dockAppsChoice);
        dockAppsChoice.setVisibility(config.dockRow ? View.VISIBLE : View.GONE);
        dockAppsChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_DOCK_EDIT, true)); finish();
        });
        performanceChoice = findViewById(R.id.performanceChoice);
        performanceChoice.setOnClickListener(v -> {
            boolean enabled = !choices.getBoolean("reduced_motion", config.reducedMotion);
            choices.edit().putBoolean("reduced_motion", enabled).putBoolean("animations", !enabled)
                    .putBoolean("advanced_effects", false).apply();
            changed(); refresh();
        });
        backupExportChoice = findViewById(R.id.backupExportChoice);
        backupImportChoice = findViewById(R.id.backupImportChoice);
        backupExportChoice.setOnClickListener(v -> createBackup());
        backupImportChoice.setOnClickListener(v -> openBackup());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(OpenOnStart.KEY)
                    .remove("home_idle_clock_seconds").remove("wallpaper_dim")
                    .remove("wallpaper_rotation_minutes").remove("home_card_order")
                    .remove("clock_show_prayer").remove("clock_show_date")
                    .remove("clock_content_order").remove("clock_digit_style").remove("clock_format")
                    .remove("clock_brightness")
                    .remove("reduced_motion").commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        File picked = dir == null ? null : new File(dir, "wallpaper-picked.jpg");
        boolean hasPicked = picked != null && picked.isFile() && picked.length() > 0;
        String[] labels = new String[images.size() + 1 + (hasPicked ? 1 : 0)];
        for (int i = 0; i < images.size(); i++) labels[i] = wallpaperLabel(images.get(i));
        labels[images.size()] = getString(R.string.wallpaper_none);
        if (hasPicked) labels[images.size() + 1] = getString(R.string.wallpaper_imported);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "@device".equals(current) && hasPicked ? images.size() + 1 : "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        showWallpaperPreview(null, "none", getString(R.string.wallpaper_none));
                    } else if (hasPicked && which == images.size() + 1) {
                        showWallpaperPreview(picked, "@device", getString(R.string.wallpaper_imported));
                    } else {
                        showWallpaperPreview(images.get(which), images.get(which).getName(), labels[which]);
                    }
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setPositiveButton(R.string.wallpaper_from_device, (dialog, which) -> openDeviceWallpaperPicker())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showWallpaperPreview(File file, String choice, String label) {
        showWallpaperPreview(file, choice, label, null);
    }

    private void showWallpaperPreview(File file, String choice, String label, File pendingImport) {
        LinearLayout frame = new LinearLayout(this);
        frame.setGravity(android.view.Gravity.CENTER);
        frame.setBackgroundColor(Color.BLACK);
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setLayoutParams(new LinearLayout.LayoutParams(-1, (int) (260 * getResources().getDisplayMetrics().density)));
        frame.addView(preview);
        if (file != null) {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            opts.inSampleSize = longest > 1600 ? Math.max(1, longest / 1200) : 1;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), opts);
            if (bitmap != null) preview.setImageBitmap(bitmap);
        }
        int dim = choices.getInt("wallpaper_dim", Config.load(this).wallpaperDim);
        if (dim > 0) preview.setColorFilter(Color.argb(Math.min(220, dim * 255 / 100), 0, 0, 0));
        AlertDialog previewDialog = new AlertDialog.Builder(this).setTitle(getString(R.string.wallpaper_preview_title) + " · " + label)
                .setView(frame)
                .setPositiveButton(R.string.wallpaper_apply, (d, w) -> {
                    if (pendingImport != null) {
                        File dir = Config.dir(this); File target = dir == null ? null : new File(dir, "wallpaper-picked.jpg");
                        try (InputStream in = new FileInputStream(pendingImport); FileOutputStream out = new FileOutputStream(target)) {
                            byte[] buffer = new byte[8192]; for (int n; (n = in.read(buffer)) > 0;) out.write(buffer, 0, n);
                        } catch (Exception e) { Toast.makeText(this, R.string.wallpaper_import_failed, Toast.LENGTH_LONG).show(); return; }
                        pendingImport.delete();
                    }
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, choice).apply();
                    changed(); refresh(); Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                }).setNegativeButton(android.R.string.cancel, (d,w) -> { if (pendingImport != null) pendingImport.delete(); }).create();
        previewDialog.setOnCancelListener(d -> { if (pendingImport != null) pendingImport.delete(); });
        previewDialog.show();
    }

    private String wallpaperLabel(File file) {
        String name = file.getName();
        if (name.equals("builtin-moon-lake.jpg")) return getString(R.string.wallpaper_builtin_moon);
        if (name.equals("builtin-dunes.jpg")) return getString(R.string.wallpaper_builtin_dunes);
        if (name.equals("builtin-golden-coast.jpg")) return getString(R.string.wallpaper_builtin_coast);
        return name;
    }

    private void openDeviceWallpaperPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_WALLPAPER); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show(); }
    }

    private void showIdleClockPicker() {
        final int[] seconds = {0, 15, 30, 45, 60, 120, 300, 600, 900, 1800, 3600};
        final int[] labels = {R.string.home_idle_clock_off, R.string.home_idle_clock_15s, R.string.home_idle_clock_30s,
                R.string.home_idle_clock_45s, R.string.home_idle_clock_1m, R.string.home_idle_clock_2m,
                R.string.home_idle_clock_5m, R.string.home_idle_clock_10m, R.string.home_idle_clock_15m,
                R.string.home_idle_clock_30m, R.string.home_idle_clock_60m};
        String[] items = new String[labels.length]; int checked = 0;
        int current = choices.getInt("home_idle_clock_seconds", getResources().getInteger(R.integer.home_idle_clock_seconds));
        for (int i = 0; i < labels.length; i++) { items[i] = getString(labels[i]); if (seconds[i] == current) checked = i; }
        new AlertDialog.Builder(this).setTitle(R.string.home_idle_clock_label)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    choices.edit().putInt("home_idle_clock_seconds", seconds[which]).apply();
                    changed(); refresh(); dialog.dismiss();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void chooseWallpaperDim() {
        int[] values = {-1, 0, 20, 40, 60, 80};
        String[] labels = {getString(R.string.wallpaper_rotation_theme), "0%", "20%", "40%", "60%", "80%"};
        singleChoice(R.string.wallpaper_dim_label, labels, closest(values, choices.getInt("wallpaper_dim", -1)), i -> {
            choices.edit().putInt("wallpaper_dim", values[i]).apply(); changed(); refresh();
        });
    }

    private void chooseWallpaperRotation() {
        int[] values = {-2, 0, 15, 30, 60};
        int[] ids = {R.string.wallpaper_rotation_theme, R.string.wallpaper_rotation_off,
                R.string.wallpaper_rotation_15m, R.string.wallpaper_rotation_30m, R.string.wallpaper_rotation_60m};
        String[] labels = new String[ids.length]; for (int i = 0; i < ids.length; i++) labels[i] = getString(ids[i]);
        singleChoice(R.string.wallpaper_rotation_label, labels, closest(values, choices.getInt("wallpaper_rotation_minutes", -2)), i -> {
            choices.edit().putInt("wallpaper_rotation_minutes", values[i]).apply(); changed(); refresh();
        });
    }

    private void chooseHomeCardOrder() {
        String[] labels = {getString(R.string.home_card_prayer_weather), getString(R.string.home_card_weather_prayer)};
        singleChoice(R.string.home_card_order_label, labels, choices.getInt("home_card_order", 0), i -> {
            choices.edit().putInt("home_card_order", i).apply(); changed(); refresh();
        });
    }

    private void chooseClockContent() {
        String[] labels = {getString(R.string.clock_content_time_only), getString(R.string.clock_content_time_date),
                getString(R.string.clock_content_time_prayer_only), getString(R.string.clock_content_time_date_prayer),
                getString(R.string.clock_content_prayer_date)};
        boolean date = choices.getBoolean("clock_show_date", true), prayer = choices.getBoolean("clock_show_prayer", false);
        int selected = !date && !prayer ? 0 : date && !prayer ? 1 : !date ? 2
                : choices.getInt("clock_content_order", 0) == 1 ? 4 : 3;
        singleChoice(R.string.clock_content_label, labels, selected, i -> {
            choices.edit().putBoolean("clock_show_date", i == 1 || i == 3 || i == 4)
                    .putBoolean("clock_show_prayer", i == 2 || i == 3 || i == 4)
                    .putInt("clock_content_order", i == 4 ? 1 : 0).apply(); changed(); refresh();
        });
    }

    private void chooseClockDigits() {
        String[] labels = {getString(R.string.clock_digits_system), getString(R.string.clock_digits_western),
                getString(R.string.clock_digits_arabic)};
        singleChoice(R.string.clock_digits_label, labels, choices.getInt("clock_digit_style", 0), i -> {
            choices.edit().putInt("clock_digit_style", i).apply(); changed(); refresh();
        });
    }

    private void chooseClockFormat() {
        String[] labels = {getString(R.string.clock_format_system), getString(R.string.clock_format_12),
                getString(R.string.clock_format_24)};
        singleChoice(R.string.clock_format_label, labels, choices.getInt("clock_format", 0), i -> {
            choices.edit().putInt("clock_format", i).apply(); changed(); refresh();
        });
    }

    private void chooseClockBrightness() {
        int[] values = {30, 50, 70, 90, 100};
        String[] labels = {"30%", "50%", "70%", "90%", "100%"};
        int selected = closest(values, choices.getInt("clock_brightness", 100));
        singleChoice(R.string.clock_brightness_label, labels, selected, i -> {
            choices.edit().putInt("clock_brightness", values[i]).apply(); changed(); refresh();
        });
    }

    private interface ChoiceAction { void selected(int index); }
    private void singleChoice(int title, String[] labels, int selected, ChoiceAction action) {
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected, (dialog, which) -> {
            action.selected(which); dialog.dismiss();
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private static int closest(int[] values, int target) {
        for (int i = 0; i < values.length; i++) if (values[i] == target) return i;
        return 0;
    }

    private void createBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "cleanyourtv-settings.json");
        try { startActivityForResult(intent, EXPORT_BACKUP); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show(); }
    }

    private void openBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        try { startActivityForResult(intent, IMPORT_BACKUP); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show(); }
    }

    private static final String[] BACKUP_KEYS = {"columns", Config.ROWS_BELOW_DOCK_KEY, "animations", "advanced_effects",
            Config.WALLPAPER_CHOICE_KEY, Config.DOCK_OPACITY_KEY, OpenOnStart.KEY, "home_idle_clock_seconds",
            "wallpaper_dim", "wallpaper_rotation_minutes", "home_card_order", "clock_show_prayer", "clock_show_date",
            "clock_content_order", "clock_digit_style", "clock_format", "clock_brightness", "reduced_motion"};

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_WALLPAPER) { importWallpaper(uri); return; }
        if (requestCode == EXPORT_BACKUP) { exportBackup(uri); return; }
        if (requestCode == IMPORT_BACKUP) importBackup(uri);
    }

    private void importWallpaper(Uri uri) {
        File dir = Config.dir(this);
        if (dir == null) { Toast.makeText(this, R.string.wallpaper_import_failed, Toast.LENGTH_LONG).show(); return; }
        File candidate = new File(dir, "wallpaper-candidate.tmp");
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(candidate)) {
            if (in == null) throw new java.io.IOException("No image stream");
            byte[] buffer = new byte[8192]; long total = 0;
            for (int n; (n = in.read(buffer)) > 0;) {
                total += n; if (total > 20L * 1024 * 1024) throw new java.io.IOException("Image exceeds 20 MB");
                out.write(buffer, 0, n);
            }
            showWallpaperPreview(candidate, "@device", getString(R.string.wallpaper_imported), candidate);
        } catch (Exception e) { candidate.delete(); Toast.makeText(this, R.string.wallpaper_import_failed, Toast.LENGTH_LONG).show(); }
    }

    private void exportBackup(Uri uri) {
        try {
            JSONObject values = new JSONObject();
            java.util.Map<String, ?> all = choices.getAll();
            for (String key : BACKUP_KEYS) if (all.containsKey(key)) values.put(key, all.get(key));
            String wallpaperChoice = choices.getString(Config.WALLPAPER_CHOICE_KEY, "");
            File wall = null;
            File dir = Config.dir(this);
            if ("@device".equals(wallpaperChoice) && dir != null) wall = new File(dir, "wallpaper-picked.jpg");
            else if (!wallpaperChoice.isEmpty() && !"none".equals(wallpaperChoice) && dir != null) {
                wall = WallpaperRotation.find(WallpaperRotation.images(new File(dir, "wallpapers")), wallpaperChoice);
                if (wall == null && wallpaperChoice.equals(Config.WALLPAPER_NAME)) wall = new File(dir, Config.WALLPAPER_NAME);
            }
            if (wall != null && wall.isFile()) {
                if (wall.length() > 8 * 1024 * 1024) { Toast.makeText(this, R.string.backup_wallpaper_too_large, Toast.LENGTH_LONG).show(); return; }
                try (InputStream in = new FileInputStream(wall); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] b = new byte[8192]; for (int n; (n = in.read(b)) > 0;) out.write(b, 0, n);
                values.put("wallpaper_image", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                }
            }
            File order = Order.file(this);
            if (order.isFile() && order.length() <= 128 * 1024) {
                try (InputStream in = new FileInputStream(order); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] b = new byte[4096]; for (int n; (n = in.read(b)) > 0;) out.write(b, 0, n);
                    values.put("tile_order", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                }
            }
            JSONObject backup = new JSONObject().put("format", "cleanyourtv-settings-1").put("settings", values);
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new java.io.IOException("No output stream");
                out.write(backup.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Toast.makeText(this, R.string.backup_export_done, Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show(); }
    }

    private void importBackup(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("No input stream");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0;) { if (out.size() + n > 12 * 1024 * 1024) throw new java.io.IOException("Backup too large"); out.write(b, 0, n); }
            JSONObject backup = new JSONObject(out.toString("UTF-8"));
            if (!"cleanyourtv-settings-1".equals(backup.optString("format"))) throw new java.io.IOException("Unsupported backup");
            JSONObject values = backup.getJSONObject("settings"); SharedPreferences.Editor edit = choices.edit();
            for (String key : BACKUP_KEYS) {
                Object value = values.opt(key); if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                else if (value instanceof Long) edit.putLong(key, (Long) value);
                else if (value instanceof String) edit.putString(key, (String) value);
            }
            byte[] restoredWallpaper = null;
            if (values.has("wallpaper_image")) {
                restoredWallpaper = Base64.decode(values.getString("wallpaper_image"), Base64.DEFAULT);
                if (restoredWallpaper.length > 8 * 1024 * 1024) throw new java.io.IOException("Wallpaper too large");
                if (Config.dir(this) == null) throw new java.io.IOException("Storage unavailable");
            }
            edit.apply();
            if (restoredWallpaper != null) {
                try (FileOutputStream wall = new FileOutputStream(new File(Config.dir(this), "wallpaper-picked.jpg"))) { wall.write(restoredWallpaper); }
                choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "@device").apply();
            }
            if (values.has("tile_order")) {
                byte[] order = Base64.decode(values.getString("tile_order"), Base64.DEFAULT);
                try (FileOutputStream target = new FileOutputStream(Order.file(this))) { target.write(order); }
            }
            changed(); refresh(); Toast.makeText(this, R.string.backup_import_done, Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show(); }
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : "@device".equals(wallpaperCurrent) ? getString(R.string.wallpaper_imported)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        int idle = choices.getInt("home_idle_clock_seconds", getResources().getInteger(R.integer.home_idle_clock_seconds));
        int idleLabel = idle == 0 ? R.string.home_idle_clock_off : idle <= 15 ? R.string.home_idle_clock_15s
                : idle <= 30 ? R.string.home_idle_clock_30s : idle <= 45 ? R.string.home_idle_clock_45s
                : idle <= 60 ? R.string.home_idle_clock_1m : idle <= 120 ? R.string.home_idle_clock_2m
                : idle <= 300 ? R.string.home_idle_clock_5m : idle <= 600 ? R.string.home_idle_clock_10m
                : idle <= 900 ? R.string.home_idle_clock_15m : idle <= 1800 ? R.string.home_idle_clock_30m : R.string.home_idle_clock_60m;
        homeIdleClockChoice.setText(getString(R.string.home_idle_clock_state, getString(idleLabel)));
        int dim = choices.getInt("wallpaper_dim", Config.load(this).wallpaperDim);
        wallpaperDimChoice.setText(getString(R.string.wallpaper_dim_state,
                choices.getInt("wallpaper_dim", -1) < 0 ? getString(R.string.wallpaper_rotation_theme) : dim + "%"));
        int rotation = choices.getInt("wallpaper_rotation_minutes", -2);
        int rotationLabel = rotation == -2 ? R.string.wallpaper_rotation_theme : rotation == 0 ? R.string.wallpaper_rotation_off
                : rotation <= 15 ? R.string.wallpaper_rotation_15m : rotation <= 30 ? R.string.wallpaper_rotation_30m : R.string.wallpaper_rotation_60m;
        wallpaperRotationChoice.setText(getString(R.string.wallpaper_rotation_state, getString(rotationLabel)));
        int cardOrder = choices.getInt("home_card_order", 0);
        homeCardOrderChoice.setText(getString(R.string.home_card_order_state,
                getString(cardOrder == 0 ? R.string.home_card_prayer_weather : R.string.home_card_weather_prayer)));
        int clockOrder = choices.getInt("clock_content_order", 0);
        boolean showDate = choices.getBoolean("clock_show_date", true), showPrayer = choices.getBoolean("clock_show_prayer", false);
        int clockLabel = !showDate && !showPrayer ? R.string.clock_content_time_only
                : showDate && !showPrayer ? R.string.clock_content_time_date : !showDate ? R.string.clock_content_time_prayer_only
                : clockOrder == 1 ? R.string.clock_content_prayer_date : R.string.clock_content_time_date_prayer;
        clockContentChoice.setText(getString(R.string.clock_content_state, getString(clockLabel)));
        int digitStyle = choices.getInt("clock_digit_style", 0);
        int digitsLabel = digitStyle == 1 ? R.string.clock_digits_western : digitStyle == 2
                ? R.string.clock_digits_arabic : R.string.clock_digits_system;
        clockDigitsChoice.setText(getString(R.string.clock_digits_state, getString(digitsLabel)));
        int clockFormat = choices.getInt("clock_format", 0);
        int formatLabel = clockFormat == 1 ? R.string.clock_format_12 : clockFormat == 2
                ? R.string.clock_format_24 : R.string.clock_format_system;
        clockFormatChoice.setText(getString(R.string.clock_format_state, getString(formatLabel)));
        clockBrightnessChoice.setText(getString(R.string.clock_brightness_state, choices.getInt("clock_brightness", 100)));
        dockAppsChoice.setText(R.string.dock_apps_settings);
        performanceChoice.setText(getString(R.string.performance_state,
                getString(choices.getBoolean("reduced_motion", Config.load(this).reducedMotion) ? R.string.on : R.string.off)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(OpenOnStart.KEY) || choices.contains("home_idle_clock_seconds")
                || choices.contains("wallpaper_dim") || choices.contains("wallpaper_rotation_minutes")
                || choices.contains("home_card_order") || choices.contains("clock_show_prayer")
                || choices.contains("clock_show_date") || choices.contains("clock_digit_style")
                || choices.contains("clock_format") || choices.contains("clock_brightness") || choices.contains("reduced_motion");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
