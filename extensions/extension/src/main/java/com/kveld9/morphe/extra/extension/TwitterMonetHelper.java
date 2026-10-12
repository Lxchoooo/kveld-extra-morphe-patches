package com.kveld9.morphe.extra.extension;

import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import java.lang.reflect.Method;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

public final class TwitterMonetHelper {
    // Embedded hardcoded ARGB fallbacks from X/Twitter 12.33.1 disassembly
    private static final int FALLBACK_ACCENT = 0xFF1D9BF0; // Canonical Twitter Blue (z1.J1)
    private static final int FALLBACK_ACCENT_CONTAINER_LIGHT = 0xFFEFAFFF; // Light unread badge (z1.a)
    private static final int FALLBACK_ACCENT_CONTAINER_DARK = 0xFF163043; // Dim unread badge

    // Cached palette values
    private static int cachedUiMode = -1;
    private static int cachedAccent = FALLBACK_ACCENT;
    private static int cachedAccentLight = FALLBACK_ACCENT;
    private static int cachedAccentDark = FALLBACK_ACCENT;
    private static int cachedAccentContainer = FALLBACK_ACCENT_CONTAINER_LIGHT;
    private static int cachedAccentContainerLight = FALLBACK_ACCENT_CONTAINER_LIGHT;
    private static int cachedAccentContainerDark = FALLBACK_ACCENT_CONTAINER_DARK;

    private TwitterMonetHelper() {
    }

    private static Application getApplication() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("L");
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication");
            currentApplicationMethod.setAccessible(true);
            return (Application) currentApplicationMethod.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int resolveSystemColor(Resources res, String name, int fallback) {
        try {
            int id = res.getIdentifier(name, "color", "android");
            if (id != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return res.getColor(id, null);
                } else {
                    return res.getColor(id);
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static synchronized void ensureCache() {
        try {
            Application app = getApplication();
            if (app == null) return;
            Resources res = app.getResources();
            if (res == null) return;
            Configuration config = res.getConfiguration();
            if (config == null) return;

            int currentUiMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentUiMode == cachedUiMode) return;

            // Primary: system_accent1_600 (light) / system_accent1_200 (dark)
            cachedAccentLight = resolveSystemColor(res, "system_accent1_600", FALLBACK_ACCENT);
            cachedAccentDark = resolveSystemColor(res, "system_accent1_200", FALLBACK_ACCENT);

            // Container: system_accent1_100 (light) / system_accent1_700 (dark)
            cachedAccentContainerLight = resolveSystemColor(res, "system_accent1_100", FALLBACK_ACCENT_CONTAINER_LIGHT);
            cachedAccentContainerDark = resolveSystemColor(res, "system_accent1_700", FALLBACK_ACCENT_CONTAINER_DARK);

            boolean isNight = (currentUiMode == Configuration.UI_MODE_NIGHT_YES);
            cachedAccent = isNight ? cachedAccentDark : cachedAccentLight;
            cachedAccentContainer = isNight ? cachedAccentContainerDark : cachedAccentContainerLight;

            cachedUiMode = currentUiMode;
            System.out.println("[Dynamic Color] Refreshed Monet palette cache for uiMode " + currentUiMode + ".");
        } catch (Throwable t) {
            // Keep existing or fallback values
        }
    }

    public static int accent() {
        ensureCache();
        return cachedAccent;
    }

    public static int accentLight() {
        ensureCache();
        return cachedAccentLight;
    }

    public static int accentDark() {
        ensureCache();
        return cachedAccentDark;
    }

    public static int accentContainer() {
        ensureCache();
        return cachedAccentContainer;
    }

    public static int accentContainerLight() {
        ensureCache();
        return cachedAccentContainerLight;
    }

    public static int accentContainerDark() {
        ensureCache();
        return cachedAccentContainerDark;
    }
}
