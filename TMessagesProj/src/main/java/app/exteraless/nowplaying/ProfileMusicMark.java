package app.exteraless.nowplaying;

import android.text.TextUtils;

import java.util.Locale;

public final class ProfileMusicMark {

    private static final String PREFIX = ".oelfm.";
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final int MAX_LENGTH = 32;
    private static final int CHECKSUM_MASK = 0xFFF;
    private static final int CHECKSUM_LENGTH = 3;

    private ProfileMusicMark() {
    }

    public static String stamp(String fileName, String nick, long ownerId) {
        String base = strip(fileName);
        if (TextUtils.isEmpty(base)) {
            base = "audio.mp3";
        }
        String lower = nick == null ? "" : nick.toLowerCase();
        if (!isValid(lower)) {
            return base;
        }
        int dot = base.lastIndexOf('.');
        String name = dot > 0 ? base.substring(0, dot) : base;
        String extension = dot > 0 ? base.substring(dot) : "";
        return name + PREFIX + lower + "."
                + String.format(Locale.US, "%03x", checksum(ownerId)) + extension;
    }

    public static String nickFrom(String fileName, long ownerId) {
        if (TextUtils.isEmpty(fileName)) {
            return null;
        }
        int at = fileName.indexOf(PREFIX);
        if (at < 0) {
            return null;
        }
        int nickAt = at + PREFIX.length();
        int nickEnd = fileName.indexOf('.', nickAt);
        if (nickEnd < 0 || nickEnd + 1 + CHECKSUM_LENGTH > fileName.length()) {
            return null;
        }
        String nick = fileName.substring(nickAt, nickEnd);
        if (!isValid(nick)) {
            return null;
        }
        String stamped = fileName.substring(nickEnd + 1, nickEnd + 1 + CHECKSUM_LENGTH);
        if (!stamped.equals(String.format(Locale.US, "%03x", checksum(ownerId)))) {
            return null;
        }
        return nick;
    }

    public static String strip(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return fileName;
        }
        int at = fileName.indexOf(PREFIX);
        if (at < 0) {
            return fileName;
        }
        int nickAt = at + PREFIX.length();
        int nickEnd = fileName.indexOf('.', nickAt);
        if (nickEnd < 0 || nickEnd + 1 + CHECKSUM_LENGTH > fileName.length()) {
            return fileName;
        }
        return fileName.substring(0, at) + fileName.substring(nickEnd + 1 + CHECKSUM_LENGTH);
    }

    private static boolean isValid(String nick) {
        if (TextUtils.isEmpty(nick) || nick.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < nick.length(); i++) {
            if (CHARSET.indexOf(nick.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int checksum(long ownerId) {
        long h = ownerId * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) (h & CHECKSUM_MASK);
    }
}
