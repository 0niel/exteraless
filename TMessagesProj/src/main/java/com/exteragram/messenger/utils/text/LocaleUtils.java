package com.exteragram.messenger.utils.text;

import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Шим {@code com.exteragram.messenger.utils.text.LocaleUtils}.
 *
 * dex-модули плагинов скомпилированы против него напрямую (MandreTweaks зовёт
 * {@code formatWithUsernames} и {@code fullyFormatText} в конструкторе строки
 * каталога), поэтому подстановка имён из class_aliases.py тут не работает —
 * класс должен существовать под настоящим именем.
 */
public abstract class LocaleUtils {

    private static final Pattern MARKDOWN_LINK_PATTERN =
            Pattern.compile("\\[([^]]+?)]\\(([^)\\s]+)\\)");

    public static String ensureUrlHasHttps(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.contains("://")) {
            return url;
        }
        return AndroidUtilities.WEB_URL != null && AndroidUtilities.WEB_URL.matcher(url).matches()
                ? "https://".concat(url) : url;
    }

    public static CharSequence fromHtml(String html) {
        return new SpannableString(Html.fromHtml(html, 0));
    }

    public static CharSequence formatWithURLs(CharSequence text) {
        if (TextUtils.isEmpty(text) || AndroidUtilities.WEB_URL == null) {
            return text;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Matcher matcher = AndroidUtilities.WEB_URL.matcher(text);
        while (matcher.find()) {
            try {
                builder.setSpan(new URLSpanNoUnderline(ensureUrlHasHttps(matcher.group(0))),
                        matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return builder;
    }

    public static CharSequence formatWithUsernames(CharSequence text) {
        return formatWithUsernames(text, LaunchActivity.getSafeLastFragment());
    }

    public static CharSequence formatWithUsernames(CharSequence text, BaseFragment fragment) {
        return formatWithUsernames(text, fragment, null);
    }

    public static CharSequence formatWithUsernames(CharSequence text, final BaseFragment fragment,
                                                   final Runnable onClick) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '@') {
                start = i;
                continue;
            }
            if (start == -1) {
                continue;
            }
            int end = i + 1;
            if (end != text.length() && (Character.isLetterOrDigit(text.charAt(end))
                    || text.charAt(end) == '_')) {
                continue;
            }
            if (end - start > 1) {
                URLSpan[] existing = builder.getSpans(start, end, URLSpan.class);
                if (existing == null || existing.length == 0) {
                    final String username = text.subSequence(start, end).toString();
                    try {
                        builder.setSpan(new URLSpanNoUnderline(username) {
                            @Override
                            public void onClick(View view) {
                                if (onClick != null) {
                                    onClick.run();
                                }
                                if (fragment == null || fragment.getMessagesController() == null) {
                                    return;
                                }
                                fragment.getMessagesController()
                                        .openByUserName(username.substring(1), fragment, 1);
                            }
                        }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
            start = -1;
        }
        return builder;
    }

    public static void parseMarkdownLinks(CharSequence[] holder) {
        parseMarkdownLinks(holder, null);
    }

    public static void parseMarkdownLinks(CharSequence[] holder, final Runnable onClick) {
        if (holder == null || holder.length == 0 || holder[0] == null) {
            return;
        }
        CharSequence text = holder[0];
        Spannable spannable = text instanceof Spannable
                ? (Spannable) text
                : Spannable.Factory.getInstance().newSpannable(text.toString());
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(spannable);
        ArrayList<String> sources = new ArrayList<>();
        ArrayList<CharSequence> replacements = new ArrayList<>();
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            if (start < 0 || end < 0 || start > end || end > spannable.length()) {
                continue;
            }
            SpannableStringBuilder label =
                    new SpannableStringBuilder(spannable.subSequence(start, end));
            label.setSpan(new URLSpanReplacement(ensureUrlHasHttps(matcher.group(2))) {
                @Override
                public void onClick(View view) {
                    if (onClick != null) {
                        onClick.run();
                    }
                    super.onClick(view);
                }
            }, 0, label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sources.add(matcher.group(0));
            replacements.add(label);
        }
        if (sources.isEmpty()) {
            return;
        }
        holder[0] = TextUtils.replace(holder[0], sources.toArray(new String[0]),
                replacements.toArray(new CharSequence[0]));
    }

    public static CharSequence fullyFormatText(CharSequence text) {
        return fullyFormatText(text, null, null);
    }

    public static CharSequence fullyFormatText(CharSequence text, BaseFragment fragment,
                                               Runnable onClick) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        CharSequence[] holder = new CharSequence[]{formatWithURLs(text)};
        parseMarkdownLinks(holder, onClick);
        CharSequence formatted = holder[0];
        return AndroidUtilities.replaceTags(fragment == null || onClick == null
                ? formatWithUsernames(formatted)
                : formatWithUsernames(formatted, fragment, onClick));
    }
}
