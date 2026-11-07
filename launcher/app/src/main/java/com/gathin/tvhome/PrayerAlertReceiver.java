package com.gathin.tvhome;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.widget.RemoteViews;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class PrayerAlertReceiver extends BroadcastReceiver {
    static final String EXTRA_PRAYER = "prayer";
    static final String EXTRA_LEAD = "lead";
    static final String EXTRA_PRAYER_INDEX = "prayer_index";
    private static final String CHANNEL_SILENT = "prayer-reminder-silent";
    private static final String CHANNEL_SOUND = "prayer-reminder-sound";

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
                != PackageManager.PERMISSION_GRANTED) {
            PrayerAlertScheduler.scheduleNext(context);
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        boolean sound = context.getSharedPreferences(PrayerAlertScheduler.PREFS, Context.MODE_PRIVATE)
                .getBoolean("sound", false);
        String channelId = sound ? CHANNEL_SOUND : CHANNEL_SILENT;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    context.getString(sound ? R.string.prayer_alert_sound_channel : R.string.prayer_alert_silent_channel),
                    NotificationManager.IMPORTANCE_HIGH);
            if (sound) channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            else channel.setSound(null, null);
            channel.enableVibration(false);
            manager.createNotificationChannel(channel);
        }
        int lead = intent.getIntExtra(EXTRA_LEAD, 10);
        String prayer = intent.getStringExtra(EXTRA_PRAYER);
        Intent open = new Intent(context, PrayerTimesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 7322, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, channelId) : new Notification.Builder(context);
        int[] icons = {R.drawable.ic_fajr, R.drawable.ic_dhuhr, R.drawable.ic_asr,
                R.drawable.ic_maghrib, R.drawable.ic_isha};
        int prayerIndex = Math.max(0, Math.min(icons.length - 1, intent.getIntExtra(EXTRA_PRAYER_INDEX, 0)));
        RemoteViews banner = new RemoteViews(context.getPackageName(), R.layout.notification_prayer_heads_up);
        banner.setImageViewResource(R.id.prayerNoticeIcon, icons[prayerIndex]);
        banner.setTextViewText(R.id.prayerNoticeTitle,
                context.getString(R.string.prayer_app_prayer_alert_notification_title));
        banner.setTextViewText(R.id.prayerNoticeText,
                context.getString(R.string.prayer_app_prayer_alert_notification_text, prayer, lead));
        builder.setSmallIcon(icons[prayerIndex]).setContentTitle(context.getString(R.string.prayer_app_prayer_alert_notification_title))
                .setContentText(context.getString(R.string.prayer_app_prayer_alert_notification_text, prayer, lead))
                .setContentIntent(content).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH)
                .setTimeoutAfter(8000L).setCustomContentView(banner).setCustomHeadsUpContentView(banner)
                .setStyle(new Notification.DecoratedCustomViewStyle());
        if (sound && Build.VERSION.SDK_INT < 26) builder.setDefaults(Notification.DEFAULT_SOUND);
        manager.notify(7323, builder.build());
        PrayerAlertScheduler.scheduleNext(context);
    }
}
