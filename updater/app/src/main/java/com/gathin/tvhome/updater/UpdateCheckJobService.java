package com.gathin.tvhome.updater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Checks the latest stable release once a day and notifies; it never downloads or installs in the background. */
public final class UpdateCheckJobService extends JobService {
    static final int JOB_ID = 7303;
    private static final String PREFS = "update_source";
    private static final String TARGET = "com.gathin.tvhome";
    private static final String CHANNEL = "release_updates";
    private Thread task;

    static String githubBaseUrl(String owner, String repo) {
        return "https://github.com/" + owner + "/" + repo + "/releases/latest/download/";
    }

    static void schedule(Context context, String owner, String repo, boolean enabled) {
        JobScheduler jobs = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        if (jobs == null) return;
        if (!enabled || !isRepoPart(owner) || !isRepoPart(repo)) {
            jobs.cancel(JOB_ID);
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateCheckJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(24L * 60 * 60 * 1000, 2L * 60 * 60 * 1000)
                .build();
        jobs.schedule(job);
    }

    private static boolean isRepoPart(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]{1,100}") && !value.equals(".") && !value.equals("..");
    }

    @Override public boolean onStartJob(JobParameters params) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String owner = prefs.getString("owner", ""), repo = prefs.getString("repo", "");
        if (!prefs.getBoolean("daily_check", true) || !isRepoPart(owner) || !isRepoPart(repo)) {
            jobFinished(params, false);
            return false;
        }
        task = new Thread(() -> {
            boolean retry = false;
            try {
                String base = githubBaseUrl(owner, repo);
                String testUrl = prefs.getString("test_url", "").trim();
                if (BuildConfig.DEBUG && !testUrl.isEmpty()) base = testUrl.endsWith("/") ? testUrl : testUrl + "/";
                if (!base.startsWith("https://") && !(BuildConfig.DEBUG && base.startsWith("http://")))
                    throw new SecurityException("Update repository must use HTTPS");
                String text = fetch(new URL(base + getString(R.string.manifest_asset)));
                ReleaseManifest m = ReleaseManifest.parse(text, TARGET);
                long installed = installedVersionCode();
                if (m.versionCode > installed) postUpdateNotification(m.versionName);
            } catch (Exception ignored) {
                // Intermittent network failures retry on the next daily run; opening the app offers a manual retry.
            }
            jobFinished(params, retry);
        }, "release-check");
        task.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        if (task != null) task.interrupt();
        return true;
    }

    private String fetch(URL url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "DigitalTV-Updater/1.0 Android");
        try {
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new java.io.IOException("HTTP " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192];
                int size = 0;
                for (int n; (n = in.read(b)) != -1;) {
                    size += n;
                    if (size > 256 * 1024) throw new java.io.IOException("Manifest too large");
                    out.write(b, 0, n);
                }
                return out.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }

    private long installedVersionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(TARGET, 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException e) { return 0; }
    }

    private void postUpdateNotification(String version) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification note = builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text, version))
                .setContentIntent(pending).setAutoCancel(true).build();
        nm.notify(1, note);
    }
}
