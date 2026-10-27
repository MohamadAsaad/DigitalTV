package com.gathin.tvhome.updater;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A small, opt-in GitHub release checker. Android's installer always owns the final confirmation. */
public final class MainActivity extends Activity {
    private static final String PREFS = "update_source";
    private static final String TARGET = "com.gathin.tvhome";
    private static final int INSTALL_PERMISSION = 71;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final int MAX_APK_BYTES = 64 * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText ownerField, repoField, testUrlField;
    private CheckBox dailyCheck;
    private TextView status;
    private ProgressBar progress;
    private Button checkButton, installButton;
    private File stagedApk;
    private String pendingApkName;
    private String pendingVersionName;
    private int pendingVersionCode;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        ownerField = findViewById(R.id.repositoryOwner);
        repoField = findViewById(R.id.repositoryName);
        dailyCheck = findViewById(R.id.dailyCheck);
        testUrlField = findViewById(R.id.testBaseUrl);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        checkButton = findViewById(R.id.checkButton);
        installButton = findViewById(R.id.installButton);
        testUrlField.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        ownerField.setText(prefs.getString("owner", ""));
        repoField.setText(prefs.getString("repo", ""));
        dailyCheck.setChecked(prefs.getBoolean("daily_check", true));
        testUrlField.setText(prefs.getString("test_url", ""));
        checkButton.setOnClickListener(v -> checkForUpdate());
        installButton.setOnClickListener(v -> installStagedUpdate());
        dailyCheck.setOnCheckedChangeListener((button, enabled) -> {
            prefs.edit().putBoolean("daily_check", enabled).apply();
            scheduleDailyCheck();
        });
        if (Build.VERSION.SDK_INT >= 33 && dailyCheck.isChecked()
                && prefs.contains("owner") && prefs.contains("repo")
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 72);
        scheduleDailyCheck();
        if (!ownerField.getText().toString().trim().isEmpty()
                && !repoField.getText().toString().trim().isEmpty()) ownerField.postDelayed(this::checkForUpdate, 350);
    }

    private void checkForUpdate() {
        String owner = ownerField.getText().toString().trim();
        String repo = repoField.getText().toString().trim();
        if (!isRepoPart(owner) || !isRepoPart(repo)) {
            status.setText(R.string.status_invalid_repo);
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("owner", owner).putString("repo", repo)
                .putString("test_url", testUrlField.getText().toString().trim()).apply();
        scheduleDailyCheck();
        installButton.setEnabled(false);
        checkButton.setEnabled(false);
        stagedApk = null;
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.status_checking);
        worker.execute(() -> {
            try {
                String base = baseUrl(owner, repo);
                ReleaseManifest manifest = ReleaseManifest.parse(
                        readText(new URL(base + getString(R.string.manifest_asset))), TARGET);
                String versionName = manifest.versionName;
                int versionCode = manifest.versionCode;
                long installed = installedVersionCode();
                if (versionCode <= installed) {
                    runOnUiThread(() -> finishCheck(getString(R.string.status_current, currentVersionName())));
                    return;
                }
                runOnUiThread(() -> status.setText(R.string.status_downloading));
                File dir = new File(getCacheDir(), "updates");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create update cache");
                File apk = new File(dir, "launcher-update.apk");
                download(new URL(base + manifest.apkName), apk);
                if (!manifest.sha256.equals(sha256(apk))) throw new SecurityException("SHA-256 does not match the release manifest");
                PackageInfo archive = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (archive == null || !TARGET.equals(archive.packageName) || versionCode(archive) != versionCode)
                    throw new SecurityException("APK package or version does not match the release manifest");
                pendingApkName = manifest.apkName;
                pendingVersionName = versionName;
                pendingVersionCode = versionCode;
                stagedApk = apk;
                runOnUiThread(() -> {
                    installButton.setEnabled(true);
                    finishCheck(getString(R.string.status_available, versionName, currentVersionName()));
                });
            } catch (java.io.FileNotFoundException e) {
                runOnUiThread(() -> finishCheck(getString(R.string.status_not_found)));
            } catch (Exception e) {
                String reason = e.getMessage();
                if (reason == null || reason.trim().isEmpty()) reason = e.getClass().getSimpleName();
                final String message = reason;
                runOnUiThread(() -> finishCheck(getString(R.string.status_failed, message)));
            }
        });
    }

    private String baseUrl(String owner, String repo) {
        String test = testUrlField.getText().toString().trim();
        if (BuildConfig.DEBUG && !test.isEmpty()) return test.endsWith("/") ? test : test + "/";
        return UpdateCheckJobService.githubBaseUrl(owner, repo);
    }

    private void scheduleDailyCheck() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        UpdateCheckJobService.schedule(this, prefs.getString("owner", ""), prefs.getString("repo", ""),
                prefs.getBoolean("daily_check", true));
    }

    private static boolean isRepoPart(String value) {
        return value.matches("[A-Za-z0-9_.-]{1,100}") && !value.equals(".") && !value.equals("..");
    }

    private String readText(URL url) throws Exception {
        HttpURLConnection c = open(url);
        try {
            if (c.getResponseCode() == 404) throw new java.io.FileNotFoundException(url.toString());
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                copyBounded(in, out, MAX_MANIFEST_BYTES);
                return out.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }

    private void download(URL url, File destination) throws Exception {
        HttpURLConnection c = open(url);
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
                copyBounded(in, out, MAX_APK_BYTES);
                out.getFD().sync();
            }
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection open(URL url) throws Exception {
        if (!"https".equalsIgnoreCase(url.getProtocol())
                && !("http".equalsIgnoreCase(url.getProtocol()) && BuildConfig.DEBUG))
            throw new SecurityException("Only HTTPS release sources are allowed");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "DigitalTV-Updater/1.0 Android");
        return c;
    }

    private static void copyBounded(InputStream in, java.io.OutputStream out, int limit) throws Exception {
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int n; (n = in.read(buffer)) != -1;) {
            total += n;
            if (total > limit) throw new java.io.IOException("Download is too large");
            out.write(buffer, 0, n);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) digest.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private long installedVersionCode() {
        try { return versionCode(getPackageManager().getPackageInfo(TARGET, 0)); }
        catch (PackageManager.NameNotFoundException e) { return 0L; }
    }

    private String currentVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(TARGET, 0);
            return info.versionName == null ? String.valueOf(versionCode(info)) : info.versionName;
        } catch (PackageManager.NameNotFoundException e) { return getString(R.string.not_installed); }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private void installStagedUpdate() {
        if (stagedApk == null || !stagedApk.isFile()) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status.setText(R.string.status_allow_install);
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), INSTALL_PERMISSION);
            } catch (Exception e) { status.setText(R.string.status_allow_install); }
            return;
        }
        try {
            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".files", stagedApk);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivity(intent);
            status.setText(R.string.status_verified);
        } catch (Exception e) {
            status.setText(getString(R.string.status_failed, e.getMessage() == null ? "Install failed" : e.getMessage()));
        }
    }

    private void finishCheck(String message) {
        progress.setVisibility(View.GONE);
        checkButton.setEnabled(true);
        status.setText(message);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INSTALL_PERMISSION && Build.VERSION.SDK_INT >= 26
                && getPackageManager().canRequestPackageInstalls() && stagedApk != null)
            installStagedUpdate();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
