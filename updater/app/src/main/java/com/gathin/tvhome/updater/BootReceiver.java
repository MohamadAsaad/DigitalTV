package com.gathin.tvhome.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Restores the opt-in periodic release check after Android restarts. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences("update_source", Context.MODE_PRIVATE);
        UpdateCheckJobService.schedule(context.getApplicationContext(), prefs.getString("owner", ""),
                prefs.getString("repo", ""), prefs.getBoolean("daily_check", true));
    }
}
