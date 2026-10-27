package com.gathin.tvhome.updater;

import org.json.JSONObject;

import java.util.Locale;

/** Strict parsing for a release manifest; asset names cannot escape the release download directory. */
final class ReleaseManifest {
    final String packageName;
    final String versionName;
    final int versionCode;
    final String apkName;
    final String sha256;

    private ReleaseManifest(String packageName, String versionName, int versionCode, String apkName, String sha256) {
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.apkName = apkName;
        this.sha256 = sha256;
    }

    static ReleaseManifest parse(String text, String expectedPackage) throws Exception {
        JSONObject json = new JSONObject(text);
        String packageName = json.getString("packageName");
        String versionName = json.getString("versionName");
        int versionCode = json.getInt("versionCode");
        String apk = json.getString("apk");
        String sha = json.getString("sha256").toLowerCase(Locale.ROOT);
        if (json.getInt("schema") != 1 || !expectedPackage.equals(packageName)
                || versionCode < 1 || versionName.trim().isEmpty()
                || apk.length() > 120 || !apk.matches("[A-Za-z0-9_.-]+\\.apk")
                || !sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid release manifest");
        return new ReleaseManifest(packageName, versionName, versionCode, apk, sha);
    }
}
