package com.gathin.tvhome;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;

/** Plays the user's local, muted looping video below the launcher's controls. */
final class WallpaperVideoPlayer implements TextureView.SurfaceTextureListener {
    private static final String TAG = "TvHome.VideoWallpaper";

    private final Context context;
    private final TextureView texture;
    private final File videoFile;
    private final Runnable onFailure;
    private MediaPlayer player;
    private Surface surface;
    private boolean surfaceAvailable;
    private boolean prepared;
    private boolean active;
    private boolean closed;
    private boolean failureReported;

    WallpaperVideoPlayer(Context context, FrameLayout root, Runnable onFailure) {
        this.context = context.getApplicationContext();
        this.onFailure = onFailure;
        File directory = Config.dir(context);
        videoFile = directory == null ? null : new File(directory, "wallpaper-background.mp4");
        texture = new TextureView(context);
        texture.setOpaque(false);
        texture.setFocusable(false);
        texture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (isEnabled()) {
            texture.setSurfaceTextureListener(this);
            root.addView(texture, Math.min(1, root.getChildCount()), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (texture.isAvailable()) onSurfaceTextureAvailable(texture.getSurfaceTexture(), texture.getWidth(), texture.getHeight());
            if (videoFile == null || !videoFile.isFile() || videoFile.length() == 0) {
                fail("Selected video file is missing", null);
            }
        }
    }

    private boolean isEnabled() {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean("wallpaper_video_enabled", false);
    }

    void start() {
        if (closed || !isEnabled() || videoFile == null || !videoFile.isFile()) return;
        active = true;
        if (prepared && player != null) {
            try { player.start(); } catch (IllegalStateException e) { prepare(); }
        } else if (surfaceAvailable) {
            prepare();
        }
    }

    void stop() {
        active = false;
        releasePlayer();
    }

    void releaseForMemoryPressure() {
        releasePlayer();
        if (active) texture.postDelayed(this::prepare, 3_000L);
    }

    void close() {
        active = false;
        closed = true;
        releasePlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        texture.setSurfaceTextureListener(null);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture textureSurface, int width, int height) {
        surfaceAvailable = true;
        if (surface != null) surface.release();
        surface = new Surface(textureSurface);
        if (active) prepare();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        surfaceAvailable = false;
        releasePlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

    private void prepare() {
        if (closed || !active || !surfaceAvailable || surface == null || player != null) return;
        try {
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setSurface(surface);
            next.setDataSource(videoFile.getAbsolutePath());
            next.setVolume(0f, 0f);
            next.setLooping(true);
            next.setOnPreparedListener(mp -> {
                if (closed || player != mp) { mp.release(); return; }
                prepared = true;
                try {
                    mp.setVolume(0f, 0f);
                    if (active) mp.start();
                } catch (IllegalStateException e) { fail("Video could not start", e); }
            });
            next.setOnErrorListener((mp, what, extra) -> {
                fail("Unsupported or unreadable video (" + what + ", " + extra + ")", null);
                return true;
            });
            next.prepareAsync();
        } catch (Exception e) {
            fail("Video preparation failed", e);
        }
    }

    private void fail(String message, Throwable error) {
        if (error == null) Log.w(TAG, message); else Log.w(TAG, message, error);
        releasePlayer();
        texture.setVisibility(View.GONE);
        if (!failureReported) {
            failureReported = true;
            if (onFailure != null) texture.post(onFailure);
        }
    }

    private void releasePlayer() {
        MediaPlayer old = player;
        player = null;
        prepared = false;
        if (old != null) {
            old.setOnPreparedListener(null);
            old.setOnErrorListener(null);
            try { old.reset(); } catch (IllegalStateException ignored) { }
            old.release();
        }
    }
}
