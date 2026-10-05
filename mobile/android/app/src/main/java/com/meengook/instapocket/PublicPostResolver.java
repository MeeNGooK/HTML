package com.meengook.instapocket;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Reads public Douyin video metadata from the shared landing page; no account or external service. */
public final class PublicPostResolver {
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final Context context;
    private final long deadline = System.currentTimeMillis() + 45000;
    private int visited;
    private String wantedId = "";
    private JSONObject match;

    public PublicPostResolver(Context context) { this.context = context; }

    public static final class Result {
        public final JSONArray items;
        Result(JSONArray items) { this.items = items; }
    }
    public static final class ResolveException extends Exception {
        public final String code;
        ResolveException(String code, String message) { super(message); this.code = code; }
    }
    private static final class Response {
        final String body; final String url;
        Response(String body, String url) { this.body = body; this.url = url; }
    }
    public Result resolve(String link) throws Exception {
        if (!UrlPolicy.isPost(link) && !UrlPolicy.isShareLink(link)) throw new ResolveException("INVALID_URL", "Douyin 공유 링크를 확인해 주세요.");
        Response page = request(link);
        if (!UrlPolicy.isDouyinHost(page.url)) throw new ResolveException("UNSAFE_REDIRECT", "공유 링크가 Douyin 이외의 주소로 연결됐어요.");
        wantedId = UrlPolicy.videoId(page.url);
        if (wantedId.isEmpty()) throw new ResolveException("UNKNOWN_VIDEO_URL", "Douyin 공개 페이지에 연결됐지만 영상 번호가 있는 주소 형식을 식별하지 못했어요.");
        parsePage(page.body);
        if (match == null) {
            List<Variant> rendered = renderVideo(page.url);
            if (!rendered.isEmpty()) return chooseBest(rendered, "웹 플레이어 공개 스트림");
            throw new ResolveException("NO_DIRECT_MEDIA", "Douyin 페이지는 열렸지만 영상 재생 주소를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
        JSONObject video = match.optJSONObject("video");
        if (video == null) throw new ResolveException("NO_DIRECT_MEDIA", "이 게시물에는 공개 동영상 스트림이 없어요.");
        List<Variant> variants = new ArrayList<>();
        JSONArray rates = video.optJSONArray("bit_rate");
        if (rates != null) for (int i = 0; i < rates.length(); i++) {
            JSONObject rate = rates.optJSONObject(i); if (rate == null) continue;
            addVariants(variants, rate.optJSONObject("play_addr"), rate.optInt("width", video.optInt("width")), rate.optInt("height", video.optInt("height")), rate.optInt("FPS", rate.optInt("fps")), rate.optString("gear_name"));
        }
        addVariants(variants, video.optJSONObject("play_addr"), video.optInt("width"), video.optInt("height"), video.optInt("fps"), "");
        if (variants.isEmpty()) throw new ResolveException("NO_DIRECT_MEDIA", "공개 페이지에서 재생 가능한 영상 파일을 찾지 못했어요.");
        return chooseBest(variants, "공개 스트림");
    }
    private Result chooseBest(List<Variant> variants, String fallbackNote) throws Exception {
        if (variants.isEmpty()) throw new ResolveException("NO_DIRECT_MEDIA", "공개 페이지에서 재생 가능한 영상 파일을 찾지 못했어요.");
        boolean has60 = variants.stream().anyMatch(v -> v.fps >= 60);
        variants.sort(Comparator.comparing((Variant v) -> v.fps >= 60).thenComparingLong(v -> (long)v.width * v.height).thenComparingInt(v -> v.fps));
        Variant best = variants.get(variants.size() - 1);
        return result(best.url, best.width, best.height, best.fps, has60 ? "60fps 우선" : fallbackNote + " · 최고 화질");
    }
    private Result result(String url, int width, int height, int fps, String note) throws Exception {
        if (!UrlPolicy.isMedia(url)) throw new ResolveException("UNTRUSTED_MEDIA", "영상 서버 주소를 확인하지 못했어요.");
        JSONArray items = new JSONArray(); JSONObject item = new JSONObject();
        item.put("url", url); item.put("type", "video"); item.put("thumbnail", "");
        item.put("quality", (width > 0 && height > 0 ? width + "×" + height + " · " : "") + (fps > 0 ? fps + "fps · " : "") + note);
        items.put(item); return new Result(items);
    }
    private static final class Variant {
        final String url; final int width, height, fps;
        Variant(String url, int width, int height, int fps) { this.url = url; this.width = width; this.height = height; this.fps = fps; }
    }
    private void addVariants(List<Variant> out, JSONObject address, int width, int height, int fps, String gear) {
        if (address == null) return;
        JSONArray urls = address.optJSONArray("url_list");
        if (urls == null) urls = address.optJSONArray("urlList");
        if (urls == null) return;
        if (fps <= 0) { Matcher m = Pattern.compile("(?i)(?:^|\\D)(60|120)(?:fps|帧)").matcher(gear); if (m.find()) fps = Integer.parseInt(m.group(1)); }
        for (int i = 0; i < urls.length(); i++) {
            String url = urls.optString(i, "").replace("\\u0026", "&");
            if (UrlPolicy.isMedia(url)) out.add(new Variant(url, width, height, fps));
        }
    }
    private Response request(String input) throws Exception {
        URL url = new URL(input);
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (System.currentTimeMillis() > deadline) throw new ResolveException("TIMEOUT", "Douyin 응답이 너무 오래 걸려요. 다시 시도해 주세요.");
            if (!UrlPolicy.isDouyinHost(url.toString())) throw new ResolveException("UNSAFE_REDIRECT", "Douyin 외부 주소로 연결되어 요청을 중단했어요.");
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setInstanceFollowRedirects(false); conn.setConnectTimeout(12000); conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", UA); conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Referer", "https://www.douyin.com/");
            int status = conn.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = conn.getHeaderField("Location"); conn.disconnect();
                if (location == null || redirects == 5) break;
                url = new URL(url, location); continue;
            }
            if (status < 200 || status >= 300) { conn.disconnect(); throw new ResolveException("PUBLIC_ACCESS_RESTRICTED", "Douyin이 이 영상의 공개 페이지 요청을 거부했어요 (" + status + ")."); }
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) { if (out.size() + count > MAX_BYTES) throw new ResolveException("PAGE_TOO_LARGE", "Douyin 페이지가 너무 커서 분석을 중단했어요."); out.write(buffer, 0, count); }
                return new Response(new String(out.toByteArray(), StandardCharsets.UTF_8), url.toString());
            } finally { conn.disconnect(); }
        }
        throw new ResolveException("REDIRECT_FAILED", "Douyin 공유 링크를 영상으로 연결하지 못했어요.");
    }
    private void parsePage(String html) {
        Matcher scripts = Pattern.compile("(?is)<script[^>]*>(.*?)</script>").matcher(html);
        while (scripts.find() && match == null) {
            String candidate = scripts.group(1).trim();
            if (candidate.length() > MAX_BYTES || !(candidate.startsWith("{") || candidate.startsWith("window."))) continue;
            int start = candidate.indexOf('{'); int end = candidate.lastIndexOf('}');
            if (start < 0 || end <= start) continue;
            candidate = candidate.substring(start, end + 1).replace("&quot;", "\"").replace("&amp;", "&").replace("&#x2F;", "/");
            try { walk(new JSONTokener(candidate).nextValue(), 0); } catch (Exception ignored) { }
        }
        if (match == null) {
            Matcher data = Pattern.compile("(?is)RENDER_DATA[^>]*>([^<]+)<").matcher(html);
            if (data.find()) try { String decoded = URLDecoder.decode(data.group(1), "UTF-8"); walk(new JSONTokener(decoded).nextValue(), 0); } catch (Exception ignored) { }
        }
    }
    /**
     * The rendered page often loads more than the video element's current source.
     * Read both the element and the browser performance log so a public high-frame-rate
     * variant is not discarded just because the player initially picked a lower stream.
     */
    private List<Variant> renderVideo(String pageUrl) throws Exception {
        if (!(context instanceof Activity)) return java.util.Collections.emptyList();
        Activity activity = (Activity)context;
        CountDownLatch done = new CountDownLatch(1);
        List<Variant> found = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicReference<WebView> active = new AtomicReference<>();
        final long[] lastNewCandidate = {0L};
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable cleanup = () -> {
            WebView view = active.getAndSet(null);
            if (view != null) {
                view.stopLoading();
                if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
                view.destroy();
            }
            done.countDown();
        };
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            WebView view = active.get();
            if (view == null) return;
            String script = "(function(){var id='" + wantedId + "',out=[];function add(u,w,h){try{var x=new URL(u,location.href);if(/^https?:$/.test(x.protocol)&&(x.searchParams.get('__vid')===id||/douyinvod|zjcdn|bytecdn/.test(x.hostname)))out.push({url:x.href,width:w||0,height:h||0})}catch(e){}}Array.from(document.querySelectorAll('video')).forEach(function(v){add(v.currentSrc||v.src,v.videoWidth,v.videoHeight)});performance.getEntriesByType('resource').forEach(function(e){add(e.name,0,0)});return JSON.stringify(out);})()";
            view.evaluateJavascript(script, encoded -> {
                try {
                    Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
                    if (decoded instanceof String) {
                        JSONArray candidates = new JSONArray((String)decoded);
                        for (int i = 0; i < candidates.length(); i++) {
                            JSONObject candidate = candidates.optJSONObject(i);
                            if (candidate == null) continue;
                            String url = candidate.optString("url");
                            if (UrlPolicy.isMedia(url) && addRenderedVariant(found, url, candidate.optInt("width"), candidate.optInt("height"))) {
                                lastNewCandidate[0] = System.currentTimeMillis();
                            }
                        }
                    }
                } catch (Exception ignored) { }
                long now = System.currentTimeMillis();
                if (now >= deadline || (!found.isEmpty() && lastNewCandidate[0] > 0 && now - lastNewCandidate[0] >= 2200)) cleanup.run();
                else handler.postDelayed(poll[0], 400);
            });
        };
        handler.post(() -> {
            try {
                WebView view = new WebView(activity);
                active.set(view);
                WebSettings settings = view.getSettings();
                settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setMediaPlaybackRequiresUserGesture(false);
                view.setAlpha(0.01f);
                view.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest request) {
                        return !UrlPolicy.isDouyinHost(request.getUrl().toString());
                    }
                    @Override public void onPageFinished(WebView v, String url) {
                        if (!UrlPolicy.isDouyinHost(url)) { cleanup.run(); return; }
                        handler.post(poll[0]);
                    }
                });
                FrameLayout root = activity.findViewById(android.R.id.content);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(2, 2, Gravity.TOP | Gravity.LEFT);
                root.addView(view, params);
                view.loadUrl(pageUrl);
            } catch (Exception e) { cleanup.run(); }
        });
        long remaining = Math.max(1, Math.min(35000, deadline - System.currentTimeMillis()));
        if (!done.await(remaining, TimeUnit.MILLISECONDS)) handler.post(cleanup);
        return found;
    }
    private static boolean addRenderedVariant(List<Variant> out, String url, int width, int height) {
        synchronized (out) {
            for (Variant value : out) if (value.url.equals(url)) return false;
            Matcher fps = Pattern.compile("(?i)(?:^|[?&_/-])(?:fps=?)?(60|120)(?:fps|[&_/-]|$)").matcher(url);
            int frameRate = fps.find() ? Integer.parseInt(fps.group(1)) : 0;
            Matcher size = Pattern.compile("(?i)(?:^|[?&_/-])(\\d{3,4})[xX](\\d{3,4})(?:[?&_/-]|$)").matcher(url);
            if (size.find()) { width = Math.max(width, Integer.parseInt(size.group(1))); height = Math.max(height, Integer.parseInt(size.group(2))); }
            out.add(new Variant(url, width, height, frameRate));
            return true;
        }
    }
    private void walk(Object value, int depth) {
        if (match != null || depth > 45 || ++visited > 150000) return;
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject)value;
            String id = obj.optString("aweme_id", obj.optString("awemeId", obj.optString("id", "")));
            JSONObject video = obj.optJSONObject("video");
            if (wantedId.equals(id) && video != null && (video.has("play_addr") || video.has("bit_rate"))) { match = obj; return; }
            for (java.util.Iterator<String> it = obj.keys(); it.hasNext() && match == null;) walk(obj.opt(it.next()), depth + 1);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray)value;
            for (int i = 0; i < array.length() && match == null; i++) walk(array.opt(i), depth + 1);
        } else if (value instanceof String) {
            String text = ((String)value).trim();
            if (text.length() < MAX_BYTES && text.contains(wantedId) && (text.startsWith("{") || text.startsWith("["))) try { walk(new JSONTokener(text).nextValue(), depth + 1); } catch (Exception ignored) { }
        }
    }
}
