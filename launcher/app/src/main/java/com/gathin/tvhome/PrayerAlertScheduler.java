package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/** Schedules a single local reminder before the next prayer; no continuous background polling. */
final class PrayerAlertScheduler {
    static final String PREFS = "prayer_alert_preferences";
    private static final int REQUEST = 7321;

    private PrayerAlertScheduler() { }

    static void set(Context context, int minutes) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", minutes > 0).putInt("minutes", minutes).apply();
        scheduleNext(context);
    }

    static void scheduleNext(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Intent intent = new Intent(context, PrayerAlertReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        alarms.cancel(pending);
        if (!prefs.getBoolean("enabled", false)) return;
        PrayerTimesRepository.Snapshot data = PrayerTimesRepository.read(context);
        if (data == null) return;
        int lead = prefs.getInt("minutes", 10);
        int[] nameIds = {R.string.prayer_name_fajr, R.string.prayer_name_dhuhr, R.string.prayer_name_asr,
                R.string.prayer_name_maghrib, R.string.prayer_name_isha};
        String[] keys = {data.times[0], data.times[2], data.times[3], data.times[4], data.times[5]};
        boolean[] allowed = new boolean[5];
        for (int i = 0; i < allowed.length; i++) allowed[i] = prefs.getBoolean("prayer_" + i, true);
        Calendar now = Calendar.getInstance();
        int minuteNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        long fireAt = -1; String prayer = "";
        for (int i = 0; i < keys.length; i++) {
            if (!allowed[i]) continue;
            int minute = parse(keys[i]);
            if (minute > minuteNow + lead) {
                long candidate = atMinute(now, minute) - lead * 60_000L;
                if (isQuiet(prefs, candidate)) continue;
                fireAt = candidate;
                prayer = context.getString(nameIds[i]);
                intent.putExtra(PrayerAlertReceiver.EXTRA_PRAYER_INDEX, i);
                break;
            }
        }
        if (fireAt < 0 && allowed[0]) {
            int fajr = parse(data.tomorrowFajr);
            if (fajr >= 0) {
                Calendar tomorrow = (Calendar) now.clone(); tomorrow.add(Calendar.DAY_OF_YEAR, 1);
                long candidate = atMinute(tomorrow, fajr) - lead * 60_000L;
                if (!isQuiet(prefs, candidate)) {
                    fireAt = candidate;
                    prayer = context.getString(nameIds[0]);
                    intent.putExtra(PrayerAlertReceiver.EXTRA_PRAYER_INDEX, 0);
                }
            }
        }
        if (fireAt <= System.currentTimeMillis()) return;
        intent.putExtra(PrayerAlertReceiver.EXTRA_PRAYER, prayer);
        intent.putExtra(PrayerAlertReceiver.EXTRA_LEAD, lead);
        pending = PendingIntent.getBroadcast(context, REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        if (Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending);
        } else if (Build.VERSION.SDK_INT >= 23) {
            // Exact-alarm access is user-controlled on current Android TV builds; keep reminders working
            // without forcing a special-access grant, accepting that the OS may deliver them later.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending);
        } else {
            alarms.setExact(AlarmManager.RTC_WAKEUP, fireAt, pending);
        }
    }

    private static boolean isQuiet(SharedPreferences prefs, long at) {
        if (!prefs.getBoolean("quiet_enabled", false)) return false;
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(at);
        int minute = time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE);
        int start = prefs.getInt("quiet_start", 22 * 60);
        int end = prefs.getInt("quiet_end", 7 * 60);
        if (start == end) return true;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    private static long atMinute(Calendar day, int minute) {
        Calendar time = (Calendar) day.clone();
        time.set(Calendar.HOUR_OF_DAY, minute / 60); time.set(Calendar.MINUTE, minute % 60);
        time.set(Calendar.SECOND, 0); time.set(Calendar.MILLISECOND, 0);
        return time.getTimeInMillis();
    }

    private static int parse(String value) {
        if (value == null || value.length() < 5) return -1;
        try { return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3, 5)); }
        catch (NumberFormatException e) { return -1; }
    }
}
