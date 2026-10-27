package com.gathin.tvhome.updater;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ReleaseManifestTest {
    private static String valid(String apk) {
        return "{\"schema\":1,\"packageName\":\"com.gathin.tvhome\",\"versionName\":\"3.1\","
                + "\"versionCode\":10,\"apk\":\"" + apk + "\",\"sha256\":\""
                + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}";
    }

    @Test public void parsesExpectedRelease() throws Exception {
        ReleaseManifest m = ReleaseManifest.parse(valid("launcher.apk"), "com.gathin.tvhome");
        assertEquals(10, m.versionCode);
        assertEquals("launcher.apk", m.apkName);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnUnexpectedPackage() throws Exception {
        ReleaseManifest.parse(valid("launcher.apk"), "someone.elses.app");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPathTraversal() throws Exception {
        ReleaseManifest.parse(valid("../launcher.apk"), "com.gathin.tvhome");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingHashCharacters() throws Exception {
        String json = valid("launcher.apk").replace("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "abc");
        ReleaseManifest.parse(json, "com.gathin.tvhome");
    }
}
