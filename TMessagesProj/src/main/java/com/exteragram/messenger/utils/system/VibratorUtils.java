package com.exteragram.messenger.utils.system;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/** Шим {@code com.exteragram.messenger.utils.system.VibratorUtils}. */
public final class VibratorUtils {

    private VibratorUtils() {
    }

    /** Короткий тик по умолчанию — так же зовут из плагинов без аргументов. */
    public static void vibrate() {
        vibrate(20);
    }

    public static void vibrate(long milliseconds) {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager =
                        (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = manager != null ? manager.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            vibrator.vibrate(VibrationEffect.createOneShot(
                    milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable t) {
            FileLog.e("VibratorUtils shim: vibrate failed", t);
        }
    }
}
