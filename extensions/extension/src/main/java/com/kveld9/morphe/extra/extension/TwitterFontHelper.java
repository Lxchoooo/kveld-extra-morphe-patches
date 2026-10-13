package com.kveld9.morphe.extra.extension;

import android.annotation.TargetApi;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.TypefaceSpan;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitterFontHelper {
    private static final Map<String, Typeface> sTypefaceCache = new ConcurrentHashMap<String, Typeface>();
    private static final Set<String> sCheckedPaths = Collections.synchronizedSet(new HashSet<String>());

    private static final Pattern EMOJI_PATTERN = Pattern.compile(
        "[\uD83C-\uDBFF\uDC00-\uDFFF]+"
    );

    public static Spannable process(CharSequence input, String fontPath, String emojiFontPath) {
        if (input == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return toSpannable(input);
        }

        Typeface textTypeface = getCachedTypeface(fontPath);
        Typeface emojiTypeface = getCachedTypeface(emojiFontPath);

        if (textTypeface == null && emojiTypeface == null) {
            return toSpannable(input);
        }

        return applySpans(input, textTypeface, emojiTypeface);
    }

    private static Spannable toSpannable(CharSequence input) {
        return (input instanceof Spannable) ? (Spannable) input : new SpannableStringBuilder(input);
    }

    private static Spannable applySpans(CharSequence input, Typeface textTypeface, Typeface emojiTypeface) {
        SpannableStringBuilder ss = new SpannableStringBuilder(input);
        if (textTypeface != null) {
            applyTextSpan(ss, textTypeface, input.length());
        }
        if (emojiTypeface != null) {
            applyEmojiSpans(ss, emojiTypeface, input);
        }
        return ss;
    }

    @TargetApi(Build.VERSION_CODES.P)
    private static void applyTextSpan(SpannableStringBuilder ss, Typeface typeface, int length) {
        ss.setSpan(
            new TypefaceSpan(typeface),
            0,
            length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    @TargetApi(Build.VERSION_CODES.P)
    private static void applyEmojiSpans(SpannableStringBuilder ss, Typeface typeface, CharSequence input) {
        Matcher matcher = EMOJI_PATTERN.matcher(input);
        while (matcher.find()) {
            ss.setSpan(
                new TypefaceSpan(typeface),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }

    private static Typeface getCachedTypeface(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        Typeface cached = sTypefaceCache.get(path);
        if (cached != null) {
            return cached;
        }

        if (sCheckedPaths.contains(path)) {
            return null;
        }

        return loadTypefaceFromFile(path);
    }

    private static Typeface loadTypefaceFromFile(String path) {
        sCheckedPaths.add(path);
        try {
            File file = new File(path);
            if (!file.exists() || !file.isFile() || !file.canRead()) {
                return null;
            }
            Typeface typeface = Typeface.createFromFile(file);
            if (typeface != null) {
                sTypefaceCache.put(path, typeface);
                return typeface;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
