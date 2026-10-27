package com.majnioui.tv.prayer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

/** Read-only, local hand-off of the cached schedule to the home launcher. */
public final class PrayerTimesProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(PrayerTimesRepository.COLUMNS, 1);
        Object[] row = PrayerTimesRepository.row(getContext());
        if (row != null) cursor.addRow(row);
        return cursor;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.majnioui.prayer-times"; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("refresh_weather".equals(method) && getContext() != null) {
            PrayerTimesRepository.refreshWeatherNow(getContext().getApplicationContext());
            return Bundle.EMPTY;
        }
        return super.call(method, arg, extras);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read only"); }
}
