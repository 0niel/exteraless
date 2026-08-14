package com.exteragram.messenger.utils;

import com.google.gson.Gson;

/** Шим {@code com.exteragram.messenger.utils.AppUtils}: плагинам нужен только общий Gson. */
public final class AppUtils {

    private static volatile Gson gson;

    private AppUtils() {
    }

    public static Gson getGson() {
        if (gson == null) {
            synchronized (AppUtils.class) {
                if (gson == null) {
                    gson = new Gson();
                }
            }
        }
        return gson;
    }
}
