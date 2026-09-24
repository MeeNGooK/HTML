package com.meengook.instapocket;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UrlPolicy {
    private static final Pattern POST = Pattern.compile("^/(p|reel|reels|tv)/[A-Za-z0-9_-]+/?$");
    private UrlPolicy() {}
    private static URI parse(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() != -1) return null;
            return uri;
        } catch (Exception e) { return null; }
    }
    public static boolean isInstagram(String value) {
        URI uri = parse(value);
        if (uri == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("instagram.com") || host.equals("www.instagram.com") || host.equals("m.instagram.com");
    }
    public static boolean isPost(String value) {
        URI uri = parse(value);
        return uri != null && isInstagram(value) && POST.matcher(uri.getPath()).matches();
    }
    public static boolean isMedia(String value) {
        URI uri = parse(value);
        if (uri == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String domain : new String[]{"cdninstagram.com", "fbcdn.net", "instagram.com"}) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }
}
