package com.zmxv.RNSound;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RNSoundModule extends ReactContextBaseJavaModule {
    Map<Integer, MediaPlayer> playerPool = new HashMap<>();
    ReactApplicationContext context;
    final static Object NULL = null;

    public RNSoundModule(ReactApplicationContext context) {
        super(context);
        this.context = context;
    }

    @Override
    public String getName() {
        return "RNSound";
    }

    @ReactMethod
    public void prepare(final String fileName, final Integer key, final Callback callback) {
        MediaPlayer player = createMediaPlayer(fileName);
        if (player == null) {
            WritableMap e = Arguments.createMap();
            e.putInt("code", -1);
            e.putString("message", "resource not found");
            callback.invoke(e);
            return;
        }
        try {
            player.prepare();
        } catch (Exception e) {
        }
        this.playerPool.put(key, player);
        WritableMap props = Arguments.createMap();
        props.putDouble("duration", player.getDuration() * .001);
        callback.invoke(NULL, props);
    }

    protected MediaPlayer createMediaPlayer(final String fileName) {
        int res = this.context.getResources().getIdentifier(fileName, "raw", this.context.getPackageName());
        if (res != 0) {
            return MediaPlayer.create(this.context, res);
        }
        File file = new File(fileName);
        if (file.exists()) {
            Uri uri = Uri.fromFile(file);
            return MediaPlayer.create(this.context, uri);
        }
        return null;
    }

    @ReactMethod
    public void play(final Integer key, final Callback callback) {
        MediaPlayer player = this.playerPool.get(key);
        if (player == null) {
            callback.invoke(false);
            return;
        }
        if (player.isPlaying()) {
            return;
        }
        player.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (!mp.isLooping()) {
                    mp.setOnErrorListener(null);
                    try {
                        callback.invoke(true);
                    } catch (Exception e) {
                        /* Temp catch preventing 3rd party lib crash. Waiting for lib update.

                        java.lang.RuntimeException·Illegal callback invocation from native module.
                        This callback type only permits a single invocation from native code.
                        CallbackImpl.java:32com.facebook.react.bridge.CallbackImpl.invoke
                        RNSoundModule.java:84com.zmxv.RNSound.RNSoundModule$1.onCompletion

                        */
                    }
                }
            }
        });
        player.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                mp.setOnCompletionListener(null);
                callback.invoke(false);
                return true;
            }
        });
        player.start();
    }

    @ReactMethod
    public void pause(final Integer key) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    @ReactMethod
    public void stop(final Integer key) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null && player.isPlaying()) {
            player.pause();
            player.seekTo(0);
        }
    }

    @ReactMethod
    public void release(final Integer key) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.release();
            this.playerPool.remove(key);
        }
    }

    @ReactMethod
    public void setVolume(final Integer key, final Float left, final Float right) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.setVolume(left, right);
        }
    }

    @ReactMethod
    public void setLooping(final Integer key, final Boolean looping) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.setLooping(looping);
        }
    }

    @ReactMethod
    public void setCurrentTime(final Integer key, final Float sec) {
        MediaPlayer player = this.playerPool.get(key);
        if (player != null) {
            player.seekTo((int) Math.round(sec * 1000));
        }
    }

    @ReactMethod
    public void getCurrentTime(final Integer key, final Callback callback) {
        MediaPlayer player = this.playerPool.get(key);
        if (player == null) {
            callback.invoke(-1, false);
            return;
        }
        callback.invoke(player.getCurrentPosition() * .001, player.isPlaying());
    }

    @ReactMethod
    public void enable(final Boolean enabled) {
        // no op
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("IsAndroid", true);
        return constants;
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();

        Set<Map.Entry<Integer, MediaPlayer>> entries = playerPool.entrySet();
        for (Map.Entry<Integer, MediaPlayer> entry : entries) {
            MediaPlayer mp = entry.getValue();
            if (mp == null) {
                continue;
            }
            try {
                mp.setOnCompletionListener(null);
                mp.setOnPreparedListener(null);
                mp.setOnErrorListener(null);
                if (mp.isPlaying()) {
                    mp.stop();
                }
                mp.reset();
                mp.release();
            } catch (Exception ex) {
                Log.e("RNSoundModule", "Exception when closing audios during app exit. ", ex);
            }
        }
        entries.clear();
    }
}
