package com.cotc.etchgate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** URL helpers. Approvals match the link exactly (see the README for why); denials can also match a whole host. */
public final class Urls {
    public static final int MAX_LENGTH = 512;

    private Urls() {
    }

    /** The key a decision is stored under: the link as sent, only trimmed. */
    public static String key(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** Lower-case host, or "" if there isn't one. */
    public static String host(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            String host = new URI(raw.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /** Not too long, and no control characters or formatting codes. */
    public static boolean isSane(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < ' ' || c == 127 || c == '§') {
                return false;
            }
        }
        return true;
    }

    /** Shortened for chat. */
    public static String forDisplay(String url) {
        if (url == null) {
            return "";
        }
        return url.length() <= 70 ? url : url.substring(0, 67) + "...";
    }
}
