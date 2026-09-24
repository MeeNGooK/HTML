package com.meengook.instapocket;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.math.BigInteger;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses data returned for a public post; never executes code from Instagram. */
public final class PublicMediaParser {
    private static final Pattern SCRIPTS = Pattern.compile("<script\\b[^>]*>(.*?)</script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL);
    private final String shortcode;
    private final String mediaId;
    private final Map<String, JSONObject> items = new LinkedHashMap<>();
    private int visited;
    private boolean incomplete;
    private boolean privatePost;

    public PublicMediaParser(String shortcode) { this.shortcode = shortcode; this.mediaId = mediaId(shortcode); }
    public static String mediaId(String shortcode) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        BigInteger id = BigInteger.ZERO;
        for (char c : shortcode.toCharArray()) {
            int digit = alphabet.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("Invalid shortcode");
            id = id.shiftLeft(6).add(BigInteger.valueOf(digit));
        }
        return id.toString();
    }
    public void acceptJson(String body) {
        try {
            String value = body.trim();
            if (value.startsWith("for (;;);")) value = value.substring(9).trim();
            if (value.startsWith("for(;;);")) value = value.substring(8).trim();
            if (value.startsWith("{" ) || value.startsWith("[")) walk(new JSONTokener(value).nextValue(), 0);
        } catch (Exception ignored) { /* HTML or changed JSON schema: try the next public source. */ }
    }
    public void acceptHtml(String html) {
        Matcher scripts = SCRIPTS.matcher(html);
        while (scripts.find()) {
            String text = scripts.group(1).trim();
            if (text.startsWith("window._sharedData")) {
                int first = text.indexOf('{'), last = text.lastIndexOf('}');
                if (first >= 0 && last > first) text = text.substring(first, last + 1);
            }
            acceptJson(text);
        }
        if (items.isEmpty() && !incomplete && !privatePost) {
            Map<String, String> metadata = new LinkedHashMap<>();
            Matcher tags = META.matcher(html);
            while (tags.find()) {
                Matcher attrs = ATTRIBUTE.matcher(tags.group()); String key = "", value = "";
                while (attrs.find()) {
                    if ("property".equalsIgnoreCase(attrs.group(1))) key = attrs.group(3);
                    if ("content".equalsIgnoreCase(attrs.group(1))) value = unescape(attrs.group(3));
                }
                if (!key.isEmpty()) metadata.put(key, value);
            }
            String video = metadata.getOrDefault("og:video:secure_url", metadata.getOrDefault("og:video", ""));
            if (UrlPolicy.isMedia(video)) add(video, "video", metadata.getOrDefault("og:image", ""));
            // An og:image alone may be a reel poster or an incomplete carousel, so it is not a photo download.
        }
    }
    private void walk(Object value, int depth) throws Exception {
        if (depth > 45 || ++visited > 150000 || items.size() >= 100) return;
        if (value instanceof JSONObject) {
            JSONObject node = (JSONObject)value;
            String pk = node.optString("pk", node.optString("id", "")).split("_")[0];
            if (shortcode.equals(node.optString("code")) || shortcode.equals(node.optString("shortcode")) || mediaId.equals(pk)) {
                readMedia(node); return;
            }
            // This object is the response to our single-post logged-out query, not a recommendation feed.
            JSONObject publicNode = node.optJSONObject("if_not_gated_logged_out");
            if (publicNode != null) {
                String code = publicNode.optString("code", publicNode.optString("shortcode", ""));
                if (code.isEmpty() || shortcode.equals(code)) readMedia(publicNode);
                return;
            }
            Iterator<String> keys = node.keys();
            while (keys.hasNext()) walk(node.opt(keys.next()), depth + 1);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray)value;
            for (int i=0;i<array.length();i++) walk(array.opt(i), depth + 1);
        }
    }
    private void readMedia(JSONObject node) throws Exception {
        JSONObject owner = node.optJSONObject("user"); if (owner == null) owner = node.optJSONObject("owner");
        if (owner != null && owner.optBoolean("is_private")) { privatePost = true; return; }
        JSONArray carousel = node.optJSONArray("carousel_media");
        JSONObject sidecar = node.optJSONObject("edge_sidecar_to_children");
        if (carousel != null) {
            if (carousel.length() < node.optInt("carousel_media_count", carousel.length())) incomplete = true;
            for (int i=0;i<carousel.length();i++) if (carousel.optJSONObject(i) != null) readMedia(carousel.getJSONObject(i));
            return;
        }
        if (sidecar != null) {
            JSONArray edges = sidecar.optJSONArray("edges");
            if (edges == null) { incomplete = true; return; }
            for (int i=0;i<edges.length();i++) {
                JSONObject edge = edges.optJSONObject(i);
                if (edge != null && edge.optJSONObject("node") != null) readMedia(edge.getJSONObject("node"));
            }
            return;
        }
        JSONObject versions = node.optJSONObject("image_versions2");
        String image = versions == null ? "" : best(versions.optJSONArray("candidates"));
        if (image.isEmpty()) image = node.optString("display_url", node.optString("display_src", ""));
        String video = best(node.optJSONArray("video_versions"));
        if (video.isEmpty()) video = node.optString("video_url", "");
        boolean isVideo = node.optBoolean("is_video") || node.optInt("media_type") == 2 || "GraphVideo".equals(node.optString("__typename")) || !video.isEmpty();
        if (isVideo) {
            if (!add(video, "video", image)) incomplete = true;
        } else if (!image.isEmpty()) {
            if (!add(image, "image", "")) incomplete = true;
        }
    }
    private String best(JSONArray variants) {
        if (variants == null) return "";
        String best = ""; long area = -1;
        for (int i=0;i<variants.length();i++) {
            JSONObject item = variants.optJSONObject(i); if (item == null) continue;
            String url = item.optString("url"); long size = (long)item.optInt("width", 1) * item.optInt("height", 1);
            if (size > area && UrlPolicy.isMedia(url)) { area = size; best = url; }
        }
        return best;
    }
    private boolean add(String url, String type, String thumbnail) {
        if (!UrlPolicy.isMedia(url)) return false;
        try {
            JSONObject item = new JSONObject(); item.put("url", url); item.put("type", type); item.put("thumbnail", UrlPolicy.isMedia(thumbnail) ? thumbnail : "");
            items.put(new URI(url).getPath(), item); return true;
        } catch (Exception e) { return false; }
    }
    public JSONArray result() { JSONArray result = new JSONArray(); if (!privatePost && !incomplete) for(JSONObject item:items.values()) result.put(item); return result; }
    public boolean isPrivate() { return privatePost; }
    public boolean isIncomplete() { return incomplete; }
    public static String unescape(String text) {
        Matcher numbers = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(text); StringBuffer buffer = new StringBuffer();
        while (numbers.find()) {
            String value = numbers.group(1), replacement = numbers.group();
            try { int point = value.startsWith("x") ? Integer.parseInt(value.substring(1), 16) : Integer.parseInt(value); replacement = new String(Character.toChars(point)); } catch (Exception ignored) {}
            numbers.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        numbers.appendTail(buffer);
        return buffer.toString().replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
