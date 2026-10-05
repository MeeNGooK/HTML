package com.meengook.instapocket;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UrlPolicy {
    private static final Pattern VIDEO = Pattern.compile("^/(?:share/)?(video|note|slides)/(\\d+)/?$");
    private static final Pattern VIDEO_ID = Pattern.compile("^/(?:share/)?(?:video|note|slides)/(\\d+)/?$");
    private UrlPolicy() {}
    private static URI parse(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() != -1) return null;
            return uri;
        } catch (Exception e) { return null; }
    }
    public static boolean isDouyinHost(String value) {
        URI uri = parse(value);
        if (uri == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("douyin.com") || host.endsWith(".douyin.com") || host.equals("iesdouyin.com") || host.endsWith(".iesdouyin.com");
    }
    public static boolean isShareLink(String value) {
        URI uri = parse(value);
        return uri != null && uri.getHost().equalsIgnoreCase("v.douyin.com") && uri.getPath().matches("/[A-Za-z0-9_-]+/?");
    }
    public static boolean isPost(String value) {
        URI uri = parse(value);
        return uri != null && isDouyinHost(value) && VIDEO.matcher(uri.getPath()).matches();
    }
    public static String videoId(String value) {
        URI uri = parse(value);
        if (uri == null || !isDouyinHost(value)) return "";
        java.util.regex.Matcher path = VIDEO_ID.matcher(uri.getPath());
        if (path.matches()) return path.group(2);
        String query = uri.getRawQuery();
        if (query != null) for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && (parts[0].equals("modal_id") || parts[0].equals("aweme_id") || parts[0].equals("item_id")) && parts[1].matches("\\d{10,25}")) return parts[1];
        }
        return "";
    }
    public static boolean isMedia(String value) {
        URI uri = parse(value);
        if (uri == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String domain : new String[]{"douyinvod.com", "douyinvod.net", "bytecdn.cn", "douyin.com", "douyinpic.com", "zjcdn.com"}) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }
}
