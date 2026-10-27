package com.majnioui.tv.prayer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class PrayerAlertReceiver extends BroadcastReceiver {
    static final String EXTRA_PRAYER = "prayer";
    static final String EXTRA_LEAD = "lead";
    private static final String CHANNEL = "prayer-reminder";

    @Override public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())
                || Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())
                || Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) {
            PrayerTimesRepository.refreshIfNeeded(context);
            PrayerAlertScheduler.scheduleNext(context);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                context.getString(R.string.prayer_alert_label), NotificationManager.IMPORTANCE_DEFAULT));
        int lead = intent.getIntExtra(EXTRA_LEAD, 10);
        String prayer = intent.getStringExtra(EXTRA_PRAYER);
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 7322, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_fajr).setContentTitle(context.getString(R.string.prayer_alert_notification_title))
                .setContentText(context.getString(R.string.prayer_alert_notification_text, prayer, lead))
                .setContentIntent(content).setAutoCancel(true).setDefaults(Notification.DEFAULT_SOUND);
        manager.notify(7323, builder.build());
        PrayerAlertScheduler.scheduleNext(context);
    }
}
