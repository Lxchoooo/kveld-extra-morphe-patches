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
    private static final int FALLBACK_DIM_SURFACE = 0xFF15202B; // Dim cell and app background
    private static final int FALLBACK_DIM_SURFACE_TRANSLUCENT = 0xBF15202B; // Dim cell background @ 75% alpha
    private static final int FALLBACK_DIM_SURFACE_GLASS = 0xCC15202B; // Dim glass background @ 80% alpha

    // Cached palette values
    private static int cachedUiMode = -1;
    private static int cachedAccent = FALLBACK_ACCENT;
    private static int cachedAccentContainer = FALLBACK_ACCENT_CONTAINER_LIGHT;
    private static int cachedDimSurface = FALLBACK_DIM_SURFACE;
    private static int cachedDimSurfaceTranslucent = FALLBACK_DIM_SURFACE_TRANSLUCENT;
    private static int cachedDimSurfaceGlass = FALLBACK_DIM_SURFACE_GLASS;

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

            boolean isNight = (currentUiMode == Configuration.UI_MODE_NIGHT_YES);

            // Primary: system_accent1_600 (light) / system_accent1_200 (dark)
            String accentResName = isNight ? "system_accent1_200" : "system_accent1_600";
            cachedAccent = resolveSystemColor(res, accentResName, FALLBACK_ACCENT);

            // Accent container: system_accent1_100 (light) / system_accent1_700 (dark)
            String containerResName = isNight ? "system_accent1_700" : "system_accent1_100";
            int fallbackContainer = isNight ? FALLBACK_ACCENT_CONTAINER_DARK : FALLBACK_ACCENT_CONTAINER_LIGHT;
            cachedAccentContainer = resolveSystemColor(res, containerResName, fallbackContainer);

            // Dim Surface: system_neutral1_700 for Dark surface container
            int dimBase = resolveSystemColor(res, "system_neutral1_700", FALLBACK_DIM_SURFACE);
            cachedDimSurface = dimBase;
            cachedDimSurfaceTranslucent = (dimBase & 0x00FFFFFF) | 0xBF000000;
            cachedDimSurfaceGlass = (dimBase & 0x00FFFFFF) | 0xCC000000;

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

    public static int accentContainer() {
        ensureCache();
        return cachedAccentContainer;
    }

    public static int dimSurface() {
        ensureCache();
        return cachedDimSurface;
    }

    public static int dimSurfaceTranslucent() {
        ensureCache();
        return cachedDimSurfaceTranslucent;
    }

    public static int dimSurfaceGlass() {
        ensureCache();
        return cachedDimSurfaceGlass;
    }
}
